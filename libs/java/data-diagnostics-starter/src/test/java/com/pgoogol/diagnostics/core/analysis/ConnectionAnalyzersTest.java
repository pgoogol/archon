package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ConnectionEvent;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.activityFindings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ConnectionAnalyzersTest {

    private final ConnectionWaitAnalyzer wait = new ConnectionWaitAnalyzer(SeverityScale.of(20));

    private final ConnectionHoldAnalyzer hold = new ConnectionHoldAnalyzer(SeverityScale.of(1_000), Duration.ofMillis(100));

    @Test
    @DisplayName("czekanie krótsze niż próg nie daje wniosku, a dłuższe daje jeden z najdłuższym czasem")
    void wait_whenSomeAcquisitionsSlow_reportsLongest() {

        // given
        List<ConnectionEvent> fast = List.of(connection(19, 5, 5));
        List<ConnectionEvent> slow = List.of(connection(20, 5, 5), connection(150, 5, 5), connection(1, 5, 5));

        // when
        List<Finding> none = activityFindings(wait, fast);
        List<Finding> findings = activityFindings(wait, slow);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(none).isEmpty(),
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("CONNECTION_WAIT"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.CRITICAL),
            () -> assertThat(finding.measured()).isEqualTo(150),
            () -> assertThat(finding.title()).contains("wystąpień: 2"));
    }

    @Test
    @DisplayName("połączenie trzymane dłużej niż próg daje wniosek, nawet gdy cały czas pracowało")
    void hold_whenHeldOverThreshold_reportsWarn() {

        // given
        List<ConnectionEvent> events = List.of(connection(0, 1_200, 1_100));

        // when
        List<Finding> findings = activityFindings(hold, events);

        // then
        assertAll(
            () -> assertThat(findings).extracting(Finding::code).containsExactly("CONNECTION_HOLD"),
            () -> assertThat(findings.getFirst().severity()).isEqualTo(Severity.WARN),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(1_200));
    }

    @Test
    @DisplayName("połączenie bezczynne przez ponad 90% czasu daje wniosek już poniżej progu trzymania")
    void hold_whenMostlyIdle_reportsWarnBelowThreshold() {

        // given
        List<ConnectionEvent> events = List.of(connection(0, 300, 6));

        // when
        List<Finding> findings = activityFindings(hold, events);

        // then
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(findings.getFirst().severity()).isEqualTo(Severity.WARN),
            () -> assertThat(findings.getFirst().title()).contains("w SQL 2%"));
    }

    @Test
    @DisplayName("krótkie wypożyczenie i wypożyczenie zajęte zapytaniami nie dają wniosku")
    void hold_whenShortOrBusy_reportsNothing() {

        // given: pierwsze jest bezczynne, ale krótsze niż 100 ms; drugie pracuje przez połowę czasu
        List<ConnectionEvent> events = List.of(connection(0, 80, 1), connection(0, 400, 200));

        // when
        List<Finding> findings = activityFindings(hold, events);

        // then
        assertThat(findings).isEmpty();
    }

    private static ConnectionEvent connection(long waitMillis, long holdMillis, long sqlMillis) {

        return new ConnectionEvent(Duration.ofMillis(waitMillis), Duration.ofMillis(holdMillis),
            Duration.ofMillis(sqlMillis), 1);
    }
}
