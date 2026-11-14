package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.read;
import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.write;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.findings;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MissingBatchAnalyzerTest {

    private static final String INSERT_ITEM = "insert into order_item (order_id, sku) values (?, ?)";

    private final MissingBatchAnalyzer analyzer = new MissingBatchAnalyzer(SeverityScale.of(5), 100);

    @Test
    @DisplayName("zapis powtórzony mniej razy niż próg nie daje wniosku")
    void findings_whenWriteRepeatedBelowThreshold_returnsNothing() {

        // given
        List<DataAccessEvent> events = repeat(4, write(INSERT_ITEM));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("zapis poza batchem powtórzony tyle razy, ile wynosi próg, daje WARN")
    void findings_whenWriteRepeatedAtThreshold_reportsWarn() {

        // given
        List<DataAccessEvent> events = repeat(5, write(INSERT_ITEM));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("MISSING_BATCH"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.WARN),
            () -> assertThat(finding.measured()).isEqualTo(5),
            () -> assertThat(finding.shapes().getFirst().shape()).isEqualTo(INSERT_ITEM));
    }

    @Test
    @DisplayName("pięciokrotność progu daje CRITICAL")
    void findings_whenWriteRepeatedFiveTimesThreshold_reportsCritical() {

        // given
        List<DataAccessEvent> events = repeat(25, write(INSERT_ITEM));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL);
    }

    @Test
    @DisplayName("wykonania wysłane w batchu się nie liczą, nawet gdy jest ich dużo")
    void findings_whenWritesGoInBatches_ignoresThem() {

        // given
        List<DataAccessEvent> batched = repeat(20, write(INSERT_ITEM).batchSize(50));
        List<DataAccessEvent> single = repeat(4, write(INSERT_ITEM));
        List<DataAccessEvent> events = Stream.concat(batched.stream(), single.stream()).toList();

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("powtórzone odczyty to sprawa analizy N+1, nie batchy")
    void findings_whenReadsRepeated_ignoresThem() {

        // given
        List<DataAccessEvent> events = repeat(10, read("select * from order_item where id = ?"));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DiagnosticsMode.class)
    @DisplayName("próg domyślny to 5 wykonań w każdym trybie")
    void defaults_whenModeGiven_usesThresholdFive(DiagnosticsMode mode) {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(mode);
        MissingBatchAnalyzer defaults = MissingBatchAnalyzer.defaults(settings);
        List<DataAccessEvent> fewer = repeat(4, write(INSERT_ITEM));
        List<DataAccessEvent> enough = repeat(5, write(INSERT_ITEM));

        // when
        List<Finding> belowThreshold = findings(defaults, fewer);
        List<Finding> atThreshold = findings(defaults, enough);

        // then
        assertAll(
            () -> assertThat(belowThreshold).isEmpty(),
            () -> assertThat(atThreshold).hasSize(1));
    }
}
