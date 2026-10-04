package com.pgoogol.diagnostics.core.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.pgoogol.diagnostics.core.report.FindingFixtures.END;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.START;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.caller;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shape;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shapeFinding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LogFindingReporterTest {

    private static final String ITEMS = "select * from order_item where order_id = ?";

    private final FindingFingerprint fingerprint = new FindingFingerprint();

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogFindingReporter.LOGGER_NAME);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final UnitOfWork unit = UnitOfWorkFixtures.closedHttpUnit(START, END);

    @BeforeEach
    void captureLog() {

        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLog() {

        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("w dev jednostka z wnioskiem daje podsumowanie na INFO i wniosek na WARN")
    void report_whenDevWithFinding_logsSummaryAndFinding() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.DEV, fingerprint);
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));

        // when
        reporter.report(unit, List.of(finding));

        // then
        List<ILoggingEvent> events = appender.list;
        assertAll(
            () -> assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO, Level.WARN),
            () -> assertThat(events.getFirst().getFormattedMessage())
                .contains("Jednostka GET /orders", "operacji 0", "wniosków 1"));
    }

    @Test
    @DisplayName("linia wniosku podaje tytuł, kształt z liczbami, miejsce wywołania, odcisk i ślad")
    void report_whenFinding_writesReadableMessage() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        String expectedFingerprint = fingerprint.of(finding, unit.summary());

        // when
        reporter.report(unit, List.of(finding));

        // then
        String message = appender.list.getFirst().getFormattedMessage();
        assertThat(message).contains(
            "N_PLUS_ONE: Ten sam odczyt wykonany 37 razy | GET /orders",
            "\"" + ITEMS + "\" 37×, łącznie 412 ms, najdłużej 30 ms",
            "z OrderService.list:42 -> OrderRepository.findAllByStatus",
            "[WARN fp=" + expectedFingerprint + " trace=6e1b9f]");
    }

    @Test
    @DisplayName("dane wniosku idą też jako pola strukturalne dd.*")
    void report_whenFinding_addsStructuredFields() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        String expectedFingerprint = fingerprint.of(finding, unit.summary());

        // when
        reporter.report(unit, List.of(finding));

        // then
        Map<String, Object> fields = keyValues(appender.list.getFirst());
        assertThat(fields).containsAllEntriesOf(Map.of(
            "dd.code", "N_PLUS_ONE",
            "dd.fingerprint", expectedFingerprint,
            "dd.severity", "WARN",
            "dd.unit", "GET /orders",
            "dd.store", "postgresql",
            "dd.count", 37L,
            "dd.totalMs", 412L));
    }

    @Test
    @DisplayName("w dev jednostka bez wniosków daje samo podsumowanie na DEBUG")
    void report_whenDevWithoutFindings_logsSummaryAtDebug() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.DEV, fingerprint);

        // when
        reporter.report(unit, List.of());

        // then
        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.DEBUG);
    }

    @Test
    @DisplayName("w prod log dostaje same wnioski, bez podsumowania jednostki")
    void report_whenProd_logsOnlyFindings() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS));

        // when
        reporter.report(unit, List.of(finding));
        reporter.report(unit, List.of());

        // then
        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("waga CRITICAL trafia na poziom WARN, a pełną wagę niesie pole dd.severity")
    void report_whenCritical_logsAtWarnWithSeverityField() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        List<ShapeSummary> shapes = List.of(shape(ITEMS));
        Finding critical = new Finding("N_PLUS_ONE", Severity.CRITICAL, "Ten sam odczyt 200 razy", 200, 5, shapes);

        // when
        reporter.report(unit, List.of(critical));

        // then
        ILoggingEvent event = appender.list.getFirst();
        assertAll(
            () -> assertThat(event.getLevel()).isEqualTo(Level.WARN),
            () -> assertThat(keyValues(event)).containsEntry("dd.severity", "CRITICAL"));
    }

    @Test
    @DisplayName("bardzo długi kształt jest w logu skrócony; pełny zostaje w JSONL")
    void report_whenShapeVeryLong_shortensItInMessage() {

        // given
        LogFindingReporter reporter = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        String longShape = "select " + "a, ".repeat(200) + "b from t";
        Finding finding = shapeFinding("SLOW_OPERATION", shape(longShape));

        // when
        reporter.report(unit, List.of(finding));

        // then
        String message = appender.list.getFirst().getFormattedMessage();
        assertAll(
            () -> assertThat(message).doesNotContain(longShape),
            () -> assertThat(message).contains(longShape.substring(0, LogFindingReporter.MAX_SHAPE_LENGTH) + "…"));
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {

        List<KeyValuePair> pairs = Objects.requireNonNullElse(event.getKeyValuePairs(), List.of());
        return pairs.stream()
            .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
