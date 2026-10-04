package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.AnalysisSession;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Otwarta jednostka pracy razem z sesjami analiz i licznikiem wejść granic.
 *
 * <p>Zdarzenia przechodzą do sesji pod blokadą, więc sesja widzi je po kolei, nawet gdy
 * przychodzą z kilku wątków. Blokada to {@link ReentrantLock}, nie {@code synchronized}:
 * na JDK 21 wątek wirtualny w {@code synchronized} przypina się do nośnika.</p>
 *
 * <p>Wyjątek z analizy ląduje w logu na DEBUG, a sesja, która go rzuciła, wypada
 * z jednostki. Łapiemy też {@link LinkageError}: brak opcjonalnej klasy na classpath
 * to typowa awaria startera, a nie powód, żeby przerwać żądanie aplikacji.</p>
 */
final class RunningUnit {

    private static final Logger log = LoggerFactory.getLogger(RunningUnit.class);

    private final UnitOfWork unit;

    private final List<Session> sessions = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();

    private final AtomicInteger entries = new AtomicInteger(1);

    RunningUnit(UnitOfWork unit, List<DiagnosticAnalyzer> analyzers) {

        this.unit = unit;
        analyzers.forEach(analyzer -> start(analyzer).ifPresent(sessions::add));
    }

    UnitOfWork unit() {

        return unit;
    }

    /** Kolejna granica dołącza do jednostki zamiast otwierać własną. */
    void enter() {

        entries.incrementAndGet();
    }

    /** @return {@code true}, gdy wyszła ostatnia granica i jednostkę trzeba zamknąć */
    boolean exit() {

        return entries.decrementAndGet() == 0;
    }

    /** @return {@code false}, gdy jednostka jest już zamknięta i zdarzenie do niej nie trafiło */
    boolean record(DataAccessEvent event) {

        lock.lock();
        try {

            if (!unit.record(event)) {

                return false;
            }
            sessions.removeIf(session -> !dispatch(session, event));
            return true;
        } finally {

            lock.unlock();
        }
    }

    /** Zamyka jednostkę i zbiera wnioski ze wszystkich sesji, które przetrwały. */
    List<Finding> finish(Instant end) {

        lock.lock();
        try {

            unit.close(end);
            return sessions.stream()
                .flatMap(this::findingsOf)
                .toList();
        } finally {

            lock.unlock();
        }
    }

    private Optional<Session> start(DiagnosticAnalyzer analyzer) {

        try {

            AnalysisSession session = analyzer.start(unit);
            Objects.requireNonNull(session, "analiza zwróciła pustą sesję");
            Session started = new Session(analyzer.id(), session);
            return Optional.of(started);
        } catch (RuntimeException | LinkageError error) {

            log.debug("Analiza {} nie wystartowała w jednostce {}", analyzer.getClass().getName(), unit.id(), error);
            return Optional.empty();
        }
    }

    /** @return {@code false}, gdy sesja rzuciła wyjątek i trzeba ją odłączyć */
    private boolean dispatch(Session session, DataAccessEvent event) {

        try {

            session.analysis().onEvent(event);
            return true;
        } catch (RuntimeException | LinkageError error) {

            log.debug("Analiza {} odłączona od jednostki {} po wyjątku", session.analyzerId(), unit.id(), error);
            return false;
        }
    }

    private Stream<Finding> findingsOf(Session session) {

        try {

            List<Finding> findings = session.analysis().findings();
            List<Finding> copy = List.copyOf(findings);
            return copy.stream();
        } catch (RuntimeException | LinkageError error) {

            log.debug("Analiza {} nie oddała wniosków jednostki {}", session.analyzerId(), unit.id(), error);
            return Stream.empty();
        }
    }

    private record Session(String analyzerId, AnalysisSession analysis) {

    }
}
