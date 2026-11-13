package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.RepositoryCallEvent;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.activityFindings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RepositoryHotspotAnalyzerTest {

    private final RepositoryHotspotAnalyzer analyzer = new RepositoryHotspotAnalyzer(SeverityScale.of(10), 10);

    @Test
    @DisplayName("metoda wywołana rzadziej niż próg nie daje wniosku, a na progu daje WARN ze swoją nazwą")
    void findings_whenMethodCalledAtThreshold_reportsWarnAboutMethod() {

        // given
        List<RepositoryCallEvent> fewer = calls("findById", 9, 2);
        List<RepositoryCallEvent> enough = calls("findById", 10, 2);

        // when
        List<Finding> none = activityFindings(analyzer, fewer);
        List<Finding> findings = activityFindings(analyzer, enough);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(none).isEmpty(),
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("REPOSITORY_HOTSPOT"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.WARN),
            () -> assertThat(finding.subject()).isEqualTo("OrderRepository.findById"),
            () -> assertThat(finding.measured()).isEqualTo(10),
            () -> assertThat(finding.title()).contains("łącznie 20 ms"));
    }

    @Test
    @DisplayName("wnioski idą od metody, która zajęła łącznie najwięcej czasu")
    void findings_whenSeveralHotMethods_ordersByTotalTime() {

        // given
        List<RepositoryCallEvent> events = Stream.concat(
            calls("findById", 50, 1).stream(),
            calls("findAllByStatus", 10, 30).stream()).toList();

        // when
        List<Finding> findings = activityFindings(analyzer, events);

        // then
        assertThat(findings)
            .extracting(Finding::subject)
            .containsExactly("OrderRepository.findAllByStatus", "OrderRepository.findById");
    }

    private static List<RepositoryCallEvent> calls(String method, int count, long millisEach) {

        Duration duration = Duration.ofMillis(millisEach);
        return Stream.generate(() -> new RepositoryCallEvent("OrderRepository", method, duration, true))
            .limit(count)
            .toList();
    }
}
