package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.pgoogol.diagnostics.core.report.FindingFixtures.END;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.START;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.caller;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shape;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shapeFinding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RecentFindingsTest {

    private static final Finding N_PLUS_ONE = shapeFinding("N_PLUS_ONE", shape("select 1", caller("list", 42)));

    private static final Finding SLOW = shapeFinding("SLOW_OPERATION", shape("select 2", caller("export", 7)));

    private final FindingFingerprint fingerprint = new FindingFingerprint();

    private final UnitOfWork unit = UnitOfWorkFixtures.closedHttpUnit(START, END);

    @Test
    @DisplayName("bufor oddaje wnioski od najnowszego")
    void find_whenSeveralReported_returnsNewestFirst() {

        // given
        RecentFindings recent = new RecentFindings(10, fingerprint);
        recent.report(unit, List.of(N_PLUS_ONE));
        recent.report(unit, List.of(SLOW));

        // when
        List<ReportedFinding> found = recent.find(FindingQuery.ALL);

        // then
        assertThat(found).extracting(reported -> reported.finding().code())
            .containsExactly("SLOW_OPERATION", "N_PLUS_ONE");
    }

    @Test
    @DisplayName("ponad limit najstarszy wniosek wypada, więc pamięć jest stała")
    void report_whenOverLimit_dropsOldest() {

        // given
        RecentFindings recent = new RecentFindings(3, fingerprint);

        // when
        IntStream.range(0, 5)
            .mapToObj(index -> shapeFinding("CODE_" + index, shape("select " + index)))
            .forEach(finding -> recent.report(unit, List.of(finding)));

        // then
        assertThat(recent.find(FindingQuery.ALL)).extracting(reported -> reported.finding().code())
            .containsExactly("CODE_4", "CODE_3", "CODE_2");
    }

    @Test
    @DisplayName("filtr zawęża po kodzie, odcisku i nazwie jednostki")
    void find_whenQueryGiven_filtersByCodeFingerprintAndUnit() {

        // given
        RecentFindings recent = new RecentFindings(10, fingerprint);
        recent.report(unit, List.of(N_PLUS_ONE, SLOW));
        String slowFingerprint = fingerprint.of(SLOW, unit.summary());

        // when
        List<ReportedFinding> byCode = recent.find(new FindingQuery("N_PLUS_ONE", null, null));
        List<ReportedFinding> byFingerprint = recent.find(new FindingQuery(null, slowFingerprint, null));
        List<ReportedFinding> byUnit = recent.find(new FindingQuery(null, null, "GET /orders"));
        List<ReportedFinding> otherUnit = recent.find(new FindingQuery(null, null, "GET /customers"));

        // then
        assertAll(
            () -> assertThat(byCode).extracting(reported -> reported.finding().code()).containsExactly("N_PLUS_ONE"),
            () -> assertThat(byFingerprint).extracting(reported -> reported.finding().code())
                .containsExactly("SLOW_OPERATION"),
            () -> assertThat(byUnit).hasSize(2),
            () -> assertThat(otherUnit).isEmpty());
    }
}
