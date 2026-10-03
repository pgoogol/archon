package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Silnik diagnostyki: trzyma bieżącą jednostkę pracy wątku, rozsyła do niej zdarzenia
 * i po jej zamknięciu przepuszcza wnioski analiz przez reportery.
 *
 * <p>Granica otwarta wewnątrz innej dołącza do jej jednostki (licznik wejść), więc
 * żądanie HTTP, które woła kod oznaczony jako zadanie harmonogramu, daje jeden raport,
 * nie dwa. Jednostka zamyka się z wyjściem ostatniej granicy.</p>
 *
 * <p>Zdarzenie bez otwartej jednostki przepada, chyba że włączono
 * {@link DiagnosticsSettings#captureOutsideUnit()}: wtedy trafia do wspólnej jednostki
 * {@code startup}, a po {@link #flushOutsideUnit()} do kolejnych jednostek
 * {@code background}.</p>
 *
 * <p>Analizy wyłączone w {@link DiagnosticsSettings#disabledAnalyzers()} silnik pomija
 * od razu przy tworzeniu, więc nie kosztują nic w żadnej jednostce.</p>
 *
 * <p>Diagnostyka nie może zepsuć aplikacji: wyjątek z analizy albo reportera ląduje
 * w logu na DEBUG i nie wychodzi do wywołującego. Każda analiza i każdy reporter ma
 * osobny {@code try}, więc jeden zepsuty nie zabiera wyników pozostałym.</p>
 */
public class DiagnosticsEngine {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsEngine.class);

    private final DiagnosticsSettings settings;

    private final List<DiagnosticAnalyzer> analyzers;

    private final List<FindingReporter> reporters;

    private final Clock clock;

    private final ThreadLocal<RunningUnit> current = new ThreadLocal<>();

    private final AtomicReference<RunningUnit> outside = new AtomicReference<>();

    public DiagnosticsEngine(DiagnosticsSettings settings, List<DiagnosticAnalyzer> analyzers,
                             List<FindingReporter> reporters, Clock clock) {

        this.settings = Objects.requireNonNull(settings, "ustawienia są wymagane");
        this.analyzers = analyzers.stream()
            .filter(analyzer -> settings.analyzerEnabled(analyzer.id()))
            .toList();
        this.reporters = List.copyOf(reporters);
        this.clock = Objects.requireNonNull(clock, "zegar jest wymagany");
        if (settings.captureOutsideUnit()) {

            RunningUnit startup = startUnit("startup", UnitOfWorkType.STARTUP, null);
            outside.set(startup);
        }
    }

    public DiagnosticsEngine(DiagnosticsSettings settings, List<DiagnosticAnalyzer> analyzers,
                             List<FindingReporter> reporters) {

        this(settings, analyzers, reporters, Clock.systemUTC());
    }

    /** Otwiera jednostkę albo dołącza do już otwartej w tym wątku. */
    public UnitOfWorkScope open(String name, UnitOfWorkType type) {

        return open(name, type, null);
    }

    /**
     * Otwiera jednostkę albo dołącza do już otwartej w tym wątku; wtedy nazwa, typ
     * i {@code traceId} zostają z zewnętrznej granicy.
     */
    public UnitOfWorkScope open(String name, UnitOfWorkType type, @Nullable String traceId) {

        RunningUnit running = current.get();
        if (Objects.nonNull(running) && running.unit().isOpen()) {

            running.enter();
            return new Scope(running);
        }
        RunningUnit started = startUnit(name, type, traceId);
        current.set(started);
        return new Scope(started);
    }

    /** Dolicza operację do jednostki bieżącego wątku. */
    public void record(DataAccessEvent event) {

        Objects.requireNonNull(event, "zdarzenie jest wymagane");
        RunningUnit running = current.get();
        if (Objects.nonNull(running) && running.record(event)) {

            return;
        }
        recordOutsideUnit(event);
    }

    /** Jednostka otwarta w bieżącym wątku. */
    public Optional<UnitOfWork> current() {

        return Optional.ofNullable(current.get())
            .map(RunningUnit::unit)
            .filter(UnitOfWork::isOpen);
    }

    /**
     * Raportuje wspólną jednostkę zdarzeń spoza granic i otwiera następną, typu
     * {@code background}. Warstwa integracji woła to po starcie aplikacji i przy jej
     * zamykaniu. Pusta jednostka nie daje raportu; bez {@code captureOutsideUnit}
     * metoda nic nie robi.
     */
    public void flushOutsideUnit() {

        if (!settings.captureOutsideUnit()) {

            return;
        }
        RunningUnit next = startUnit("background", UnitOfWorkType.BACKGROUND, null);
        RunningUnit previous = outside.getAndSet(next);
        if (previous.unit().operationCount() > 0) {

            finish(previous);
        }
    }

    private void recordOutsideUnit(DataAccessEvent event) {

        if (!settings.captureOutsideUnit()) {

            return;
        }
        RunningUnit target = outside.get();
        if (!target.record(event)) {

            // flushOutsideUnit właśnie podmienił wspólną jednostkę — zdarzenie idzie do następnej
            RunningUnit replacement = outside.get();
            replacement.record(event);
        }
    }

    private RunningUnit startUnit(String name, UnitOfWorkType type, @Nullable String traceId) {

        String id = newId();
        Instant start = clock.instant();
        UnitOfWork unit = new UnitOfWork(id, name, type, start, traceId, settings.unitEventLimit());
        return new RunningUnit(unit, analyzers);
    }

    private void closeUnit(RunningUnit running) {

        if (Objects.equals(current.get(), running)) {

            current.remove();
        }
        finish(running);
    }

    private void finish(RunningUnit running) {

        Instant end = clock.instant();
        List<Finding> findings = running.finish(end);
        reporters.forEach(reporter -> report(reporter, running.unit(), findings));
    }

    private void report(FindingReporter reporter, UnitOfWork unit, List<Finding> findings) {

        try {

            reporter.report(unit, findings);
        } catch (RuntimeException | LinkageError error) {

            log.debug("Reporter {} nie przyjął raportu jednostki {}", reporter.getClass().getName(), unit.id(), error);
        }
    }

    /** Osiem znaków szesnastkowych: dość, żeby odróżnić jednostki w logu z jednego okresu. */
    private String newId() {

        int random = ThreadLocalRandom.current().nextInt();
        return "%08x".formatted(random);
    }

    private final class Scope implements UnitOfWorkScope {

        private final RunningUnit running;

        private final AtomicBoolean closed = new AtomicBoolean();

        private Scope(RunningUnit running) {

            this.running = running;
        }

        @Override
        public UnitOfWork unit() {

            return running.unit();
        }

        @Override
        public void close() {

            if (closed.compareAndSet(false, true) && running.exit()) {

                closeUnit(running);
            }
        }
    }
}
