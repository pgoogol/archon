package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.MemorySnapshot;
import com.pgoogol.testdiagnostics.core.MemorySnapshotFixtures;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static com.pgoogol.testdiagnostics.report.SnapshotBuilder.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemoryReportTest {

    private static final String P = ReportLines.PREFIX;

    @Test
    @DisplayName("spokojny przebieg: limit, szczyt, zajętość po sprzątaniu, GC i pełne sprzątania z oceną OK")
    void write_whenCalm_reportsEveryRowAsOk() {

        // given: 1024 MB limitu, 400 MB szczytu, 200 MB po sprzątaniu, 300 ms GC z 10 s
        TestRunSnapshot snapshot = snapshot().withMemory(MemorySnapshotFixtures.calm()).build();

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertThat(lines).containsSubsequence(
            P + " MEMORY",
            P + "   Memory limit for tests " + ".".repeat(11) + " 1024 MB",
            P + "   Highest use (approx.) " + ".".repeat(12) + " 400 MB (39% of limit)",
            P + "   Kept after cleanup (peak) " + ".".repeat(8) + " 200 MB (20% of limit)   OK",
            P + "   Extra memory outside limit " + ".".repeat(7) + " 120 MB (program code and classes)",
            P + "   Time spent cleaning memory " + ".".repeat(7) + " 0.3 s (3% of run)   OK",
            P + "   Full memory cleanups " + ".".repeat(13) + " 0   OK");
    }

    @Test
    @DisplayName("bez żadnego sprzątania wiersz zajętości po sprzątaniu znika, a DATA podaje -1")
    void write_whenKeptAfterGcUnknown_skipsRow() {

        // given
        MemorySnapshot memory = new MemorySnapshot(1024, 300, OptionalLong.empty(), 100, 0, 0);
        TestRunSnapshot snapshot = snapshot().withMemory(memory).build();

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertAll(
            () -> assertThat(lines).noneMatch(line -> line.contains("Kept after cleanup (peak)")),
            () -> assertThat(lines).anyMatch(line -> line.contains(" heap_live_mb=-1 ")));
    }

    @Test
    @DisplayName("pamięć blisko limitu i pełne sprzątania dostają ocenę krytyczną i ostrzeżenie")
    void write_whenTight_marksCriticalAndWarning() {

        // given
        MemorySnapshot memory = new MemorySnapshot(1000, 990, OptionalLong.of(900), 100, 0, 2);
        TestRunSnapshot snapshot = snapshot().withMemory(memory).build();

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertAll(
            () -> assertThat(lines).anyMatch(line -> line.endsWith(" 900 MB (90% of limit)   CRITICAL")),
            () -> assertThat(lines).anyMatch(line -> line.endsWith(" 2   WARNING")));
    }

    @Test
    @DisplayName("po polsku oceny i opisy wartości pochodzą z polskiego pliku tekstów")
    void write_inPolish_translatesValuesAndHealth() {

        // given
        MemorySnapshot memory = new MemorySnapshot(1000, 990, OptionalLong.of(750), 100, 0, 1);
        TestRunSnapshot snapshot = snapshot().withMemory(memory).build();

        // when
        List<String> lines = render(snapshot, "pl");

        // then
        assertAll(
            () -> assertThat(lines).contains(P + "   Zajęte po sprzątaniu (szczyt) " + ".".repeat(4)
                + " 750 MB (75% limitu)   UWAGA"),
            () -> assertThat(lines).contains(P + "   Pełne sprzątania pamięci " + ".".repeat(9) + " 1   UWAGA"));
    }

    private static List<String> render(TestRunSnapshot snapshot, String language) {

        TestRunReport report = new TestRunReport(ReportMessages.forLanguage(language));
        return report.render(snapshot, "unit");
    }
}
