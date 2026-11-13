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

    private final OpenTransactions transactions = new OpenTransactions();

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
            return new EngineScope(running, false, this::closeUnit);
        }
        RunningUnit started = startUnit(name, type, traceId);
        current.set(started);
        return new EngineScope(started, true, this::closeUnit);
    }

    /**
     * Jednostka bieżącego wątku do przekazania innemu wątkowi, np. zadaniu {@code @Async}.
     * Pusta, gdy wątek nie ma otwartej jednostki.
     */
    public Optional<UnitOfWorkHandoff> handoff() {

        return Optional.ofNullable(current.get())
            .filter(running -> running.unit().isOpen())
            .map(UnitOfWorkHandoff::new);
    }

    /**
     * Dołącza bieżący wątek do przekazanej jednostki: jego zdarzenia trafiają do niej,
     * a jednostka zamyka się dopiero, gdy wyjdzie także ta granica. Puste, gdy jednostka
     * zdążyła się już zamknąć; wtedy wątek otwiera własną.
     */
    public Optional<UnitOfWorkScope> join(UnitOfWorkHandoff handoff) {

        RunningUnit running = handoff.running();
        if (!running.tryEnter()) {

            return Optional.empty();
        }
        RunningUnit previous = current.get();
        current.set(running);
        UnitOfWorkScope scope = new EngineScope(running, false, this::closeUnit, () -> restore(previous));
        return Optional.of(scope);
    }

    /** Dolicza operację do jednostki bieżącego wątku. */
    public void record(DataAccessEvent event) {

        Objects.requireNonNull(event, "zdarzenie jest wymagane");
        transactions.count(event.kind());
        RunningUnit running = current.get();
        if (Objects.nonNull(running) && running.record(event)) {

            return;
        }
        recordOutsideUnit(event);
    }

    /**
     * Przekazuje analizom zdarzenie inne niż operacja. Bez otwartej jednostki trafia do
     * wspólnej jednostki spoza granic albo przepada, tak jak operacja.
     */
    public void recordActivity(ActivityEvent event) {

        Objects.requireNonNull(event, "zdarzenie jest wymagane");
        RunningUnit running = current.get();
        if (Objects.nonNull(running) && running.recordActivity(event)) {

            return;
        }
        RunningUnit shared = outside.get();
        if (Objects.nonNull(shared)) {

            shared.recordActivity(event);
        }
    }

    /** Początek transakcji bieżącego wątku; od teraz jego operacje liczą się do niej. */
    public void beginTransaction(String name, boolean readOnly) {

        Instant start = clock.instant();
        transactions.begin(name, readOnly, start);
    }

    /** Koniec ostatnio rozpoczętej transakcji bieżącego wątku; analizy dostają jej zdarzenie. */
    public void endTransaction(TransactionOutcome outcome) {

        transactions.end(outcome).ifPresent(this::recordActivity);
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

    /**
     * Raportuje wnioski, które nie wynikają z żadnej jednostki pracy, np. ze sprawdzenia
     * konfiguracji przy starcie. Reportery dostają pustą, od razu zamkniętą jednostkę.
     */
    public void reportStandalone(String name, UnitOfWorkType type, List<Finding> findings) {

        Instant now = clock.instant();
        UnitOfWork unit = new UnitOfWork(newId(), name, type, now, null, 0);
        unit.close(now);
        List<Finding> copy = List.copyOf(findings);
        reporters.forEach(reporter -> report(reporter, unit, copy));
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

    private void restore(@Nullable RunningUnit previous) {

        if (Objects.isNull(previous)) {

            current.remove();
            return;
        }
        current.set(previous);
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
}
