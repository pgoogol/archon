package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Wnioski w logu aplikacji, pod stałym loggerem {@value #LOGGER_NAME}, żeby jednym
 * filtrem wyłowić wszystkie.
 *
 * <p>Na wniosek przypada jedno zdarzenie logu: zdanie dla człowieka, kształt z liczbami,
 * miejsce wywołania i odcisk. Te same dane idą jako pola SLF4J ({@code dd.code},
 * {@code dd.fingerprint}, {@code dd.severity}, {@code dd.unit}, {@code dd.store},
 * {@code dd.count}, {@code dd.totalMs}); przy structured logging Boota
 * ({@code logging.structured.format.console=ecs} albo {@code logstash}) stają się
 * osobnymi kluczami JSON.</p>
 *
 * <p>W dev przed wnioskami idzie linia podsumowania jednostki: na INFO, a bez wniosków na
 * DEBUG. W prod log dostaje same wnioski. Waga {@code CRITICAL} trafia na poziom WARN:
 * to wniosek o wydajności, nie błąd aplikacji, a pełną wagę niesie {@code dd.severity}.</p>
 *
 * <p>Z limitem częstości ten sam odcisk trafia do logu najwyżej raz na okno; następne
 * wypisanie podaje, ile wystąpień pominięto ({@code dd.skipped}).</p>
 */
public class LogFindingReporter implements FindingReporter {

    public static final String LOGGER_NAME = "com.pgoogol.diagnostics.findings";

    /** Długość kształtu w logu; pełny kształt jest w JSONL. */
    static final int MAX_SHAPE_LENGTH = 300;

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private static final String INDENT = "\n      ";

    private final DiagnosticsMode mode;

    private final FindingFingerprint fingerprint;

    private final FindingRateLimiter rateLimiter;

    /** Bez limitu częstości: każdy wniosek trafia do logu. */
    public LogFindingReporter(DiagnosticsMode mode, FindingFingerprint fingerprint) {

        this(mode, fingerprint, new FindingRateLimiter(Duration.ZERO));
    }

    /**
     * @param rateLimiter limit częstości według odcisku; pominięte wystąpienia dolicza się
     *                    do następnego wypisanego wniosku
     */
    public LogFindingReporter(DiagnosticsMode mode, FindingFingerprint fingerprint, FindingRateLimiter rateLimiter) {

        this.mode = Objects.requireNonNull(mode, "tryb diagnostyki jest wymagany");
        this.fingerprint = Objects.requireNonNull(fingerprint, "odcisk wniosku jest wymagany");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "limit częstości jest wymagany");
    }

    @Override
    public void report(UnitOfWork unit, List<Finding> findings) {

        UnitOfWorkSummary summary = unit.summary();
        if (Objects.equals(mode, DiagnosticsMode.DEV)) {

            logSummary(summary, findings.size());
        }
        findings.forEach(finding -> logFinding(summary, finding));
    }

    private void logSummary(UnitOfWorkSummary summary, int findingCount) {

        Level level = Level.INFO;
        if (findingCount == 0) {

            level = Level.DEBUG;
        }
        long durationMs = summary.duration()
            .map(Duration::toMillis)
            .orElse(0L);
        long databaseMs = summary.databaseTime().toMillis();
        log.atLevel(level)
            .addKeyValue("dd.unit", summary.name())
            .addKeyValue("dd.unitId", summary.id())
            .addKeyValue("dd.unitType", summary.type().name())
            .addKeyValue("dd.operations", summary.operationCount())
            .addKeyValue("dd.dbMs", databaseMs)
            .addKeyValue("dd.findings", findingCount)
            .log("Jednostka {} ({}, id {}): operacji {}, w bazie {} ms z {} ms, wniosków {}",
                summary.name(), summary.type().name(), summary.id(), summary.operationCount(),
                databaseMs, durationMs, findingCount);
    }

    private void logFinding(UnitOfWorkSummary unit, Finding finding) {

        String fingerprintValue = fingerprint.of(finding, unit);
        OptionalLong skipped = rateLimiter.tryAcquire(fingerprintValue);
        if (skipped.isEmpty()) {

            return;
        }
        Level level = levelOf(finding.severity());
        LoggingEventBuilder event = log.atLevel(level)
            .addKeyValue("dd.skipped", skipped.getAsLong())
            .addKeyValue("dd.code", finding.code())
            .addKeyValue("dd.fingerprint", fingerprintValue)
            .addKeyValue("dd.severity", finding.severity().name())
            .addKeyValue("dd.unit", unit.name())
            .addKeyValue("dd.unitId", unit.id());
        addShapeFields(event, finding);
        event.setMessage(() -> message(unit, finding, fingerprintValue, skipped.getAsLong()))
            .log();
    }

    /** Pola jedynego kształtu; wniosek o całej jednostce niesie w {@code dd.count} swój pomiar. */
    private void addShapeFields(LoggingEventBuilder event, Finding finding) {

        if (Objects.nonNull(finding.subject())) {

            event.addKeyValue("dd.subject", finding.subject());
        }
        if (finding.shapes().size() != 1) {

            event.addKeyValue("dd.count", finding.measured());
            return;
        }
        ShapeSummary shape = finding.shapes().getFirst();
        event.addKeyValue("dd.store", shape.store().name())
            .addKeyValue("dd.count", shape.count())
            .addKeyValue("dd.totalMs", shape.totalTime().toMillis());
    }

    private String message(UnitOfWorkSummary unit, Finding finding, String fingerprintValue, long skipped) {

        StringBuilder message = new StringBuilder()
            .append(finding.code()).append(": ").append(finding.title())
            .append(" | ").append(unit.name());
        finding.shapes().forEach(shape -> appendShape(message, shape));
        message.append(INDENT).append('[').append(finding.severity().name())
            .append(" fp=").append(fingerprintValue)
            .append(" trace=").append(Objects.toString(unit.traceId(), "-"))
            .append(']');
        if (skipped > 0) {

            message.append(" (+").append(skipped).append(" takich samych pominiętych w logu)");
        }
        return message.toString();
    }

    private void appendShape(StringBuilder message, ShapeSummary shape) {

        message.append(INDENT).append('"').append(shortened(shape.shape())).append('"')
            .append(' ').append(shape.count()).append("×, łącznie ")
            .append(shape.totalTime().toMillis()).append(" ms, najdłużej ")
            .append(shape.maxTime().toMillis()).append(" ms");
        shape.callers().stream()
            .findFirst()
            .ifPresent(caller -> appendCaller(message, caller));
    }

    private void appendCaller(StringBuilder message, CallSite caller) {

        String className = caller.className();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        message.append(INDENT).append("z ").append(simpleName).append('.').append(caller.method())
            .append(':').append(caller.line());
        if (Objects.nonNull(caller.repositoryMethod())) {

            message.append(" -> ").append(caller.repositoryMethod());
        }
    }

    private String shortened(String shape) {

        if (shape.length() <= MAX_SHAPE_LENGTH) {

            return shape;
        }
        return shape.substring(0, MAX_SHAPE_LENGTH) + "…";
    }

    private Level levelOf(Severity severity) {

        return switch (severity) {

            case INFO -> Level.INFO;
            case WARN, CRITICAL -> Level.WARN;
        };
    }
}
