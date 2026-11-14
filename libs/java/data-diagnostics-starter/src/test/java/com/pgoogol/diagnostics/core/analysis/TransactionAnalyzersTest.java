package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.TransactionOutcome;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.activityFindings;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransactionAnalyzersTest {

    private static final String PLACE_ORDER = "com.example.OrderService.place";

    private final LongTransactionAnalyzer longTransaction = new LongTransactionAnalyzer(SeverityScale.of(500), 10);

    private final TransactionOperationCountAnalyzer operationCount =
        new TransactionOperationCountAnalyzer(SeverityScale.of(50), 10);

    private final ReadOnlyCandidateAnalyzer readOnlyCandidate = new ReadOnlyCandidateAnalyzer(10);

    @Test
    @DisplayName("transakcja krótsza niż próg nie daje wniosku, a na progu daje WARN z jej nazwą")
    void longTransaction_whenAtThreshold_reportsWarnAboutTransaction() {

        // given
        List<TransactionEvent> fast = List.of(transaction(PLACE_ORDER, 499, 1, 1));
        List<TransactionEvent> slow = List.of(transaction(PLACE_ORDER, 500, 1, 1), transaction(PLACE_ORDER, 700, 1, 1));

        // when
        List<Finding> none = activityFindings(longTransaction, fast);
        List<Finding> findings = activityFindings(longTransaction, slow);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(none).isEmpty(),
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("LONG_TRANSACTION"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.WARN),
            () -> assertThat(finding.subject()).isEqualTo(PLACE_ORDER),
            () -> assertThat(finding.measured()).isEqualTo(700),
            () -> assertThat(finding.title()).contains("wystąpień: 2"));
    }

    @Test
    @DisplayName("transakcja pięć razy dłuższa niż próg daje CRITICAL")
    void longTransaction_whenFiveTimesThreshold_reportsCritical() {

        // given
        List<TransactionEvent> events = List.of(transaction(PLACE_ORDER, 2_500, 1, 1));

        // when
        List<Finding> findings = activityFindings(longTransaction, events);

        // then
        assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL);
    }

    @Test
    @DisplayName("liczba operacji w transakcji liczy się włącznie od progu, osobno dla każdej nazwy")
    void operationCount_whenAtThreshold_reportsPerTransactionName() {

        // given
        List<TransactionEvent> events = List.of(
            transaction(PLACE_ORDER, 10, 30, 20),
            transaction("com.example.OrderService.cancel", 10, 40, 9));

        // when
        List<Finding> findings = activityFindings(operationCount, events);

        // then
        assertAll(
            () -> assertThat(findings).extracting(Finding::subject).containsExactly(PLACE_ORDER),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(50),
            () -> assertThat(findings.getFirst().code()).isEqualTo("TRANSACTION_OPERATION_COUNT"));
    }

    @Test
    @DisplayName("zatwierdzona transakcja z samymi odczytami i bez readOnly daje wskazówkę INFO")
    void readOnlyCandidate_whenOnlyReadsWithoutFlag_reportsInfo() {

        // given
        List<TransactionEvent> events = List.of(transaction(PLACE_ORDER, 10, 3, 0), transaction(PLACE_ORDER, 10, 5, 0));

        // when
        List<Finding> findings = activityFindings(readOnlyCandidate, events);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("READ_ONLY_CANDIDATE"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.INFO),
            () -> assertThat(finding.measured()).isEqualTo(2),
            () -> assertThat(finding.subject()).isEqualTo(PLACE_ORDER));
    }

    @Test
    @DisplayName("transakcja z zapisem, już readOnly, wycofana albo pusta nie jest kandydatem")
    void readOnlyCandidate_whenWritesOrFlagOrRollbackOrEmpty_reportsNothing() {

        // given
        Duration duration = Duration.ofMillis(10);
        List<TransactionEvent> events = List.of(
            transaction("withWrite", 10, 3, 1),
            new TransactionEvent("alreadyReadOnly", DataAccessEventFixtures.TIMESTAMP, duration,
                TransactionOutcome.COMMITTED, true, 0, 3, 0, 0),
            new TransactionEvent("rolledBack", DataAccessEventFixtures.TIMESTAMP, duration,
                TransactionOutcome.ROLLED_BACK, false, 0, 3, 0, 0),
            transaction("empty", 10, 0, 0));

        // when
        List<Finding> findings = activityFindings(readOnlyCandidate, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("ponad limit nowe nazwy transakcji nie są liczone, więc pamięć jednostki jest ograniczona")
    void longTransaction_whenNamesOverLimit_ignoresNewNames() {

        // given
        LongTransactionAnalyzer limited = new LongTransactionAnalyzer(SeverityScale.of(500), 1);
        List<TransactionEvent> events = List.of(transaction("first", 600, 1, 1), transaction("second", 900, 1, 1));

        // when
        List<Finding> findings = activityFindings(limited, events);

        // then
        assertThat(findings).extracting(Finding::subject).containsExactly("first");
    }
}
