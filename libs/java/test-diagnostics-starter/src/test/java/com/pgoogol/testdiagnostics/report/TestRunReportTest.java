package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.ClassRecord;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.FailureRecord;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static com.pgoogol.testdiagnostics.report.SnapshotBuilder.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestRunReportTest {

    private static final String P = ReportLines.PREFIX;

    private static final String ORDERS = "com.example.order.OrderApiTest";

    @Test
    @DisplayName("udany przebieg: nagłówek z etykietą, werdykt, liczby i czasy wyrównane kropkami")
    void render_whenAllPassed_printsHeaderAndResult() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 9_000, 4_000, 3).build();

        // when
        List<String> lines = render(snapshot, "en", "unit");

        // then: 34 znaki na etykietę z kropkami, potem spacja i wartość
        assertThat(lines).containsSubsequence(
            P + "=".repeat(78),
            P + " TEST REPORT: UNIT TESTS",
            P + "=".repeat(78),
            P + " RESULT",
            P + "   Verdict " + ".".repeat(26) + " ALL TESTS PASSED",
            P + "   Tests run " + ".".repeat(24) + " 3  (passed 3, failed 0, skipped 0)",
            P + "   Test classes " + ".".repeat(21) + " 1",
            P + "   Total time " + ".".repeat(23) + " 10.0 s",
            P + "   Package shown below " + ".".repeat(14) + " com.example.order.*");
    }

    @Test
    @DisplayName("błędy testów i całych klas liczą się w werdykcie i trafiają do sekcji błędów")
    void render_whenProblems_countsFailedTestsAndWholeClasses() {

        // given
        TestRunSnapshot snapshot = snapshot()
            .withClass(new ClassRecord(ORDERS, 9_000, 4_000, 0, 3, 1, 0))
            .withFailure(new FailureRecord(ORDERS, "rejects empty order", "AssertionError: expected 400"))
            .withFailure(FailureRecord.ofWholeClass(ORDERS, "IllegalStateException: boom"))
            .build();

        // when
        List<String> lines = render(snapshot, "en", "unit");

        // then
        assertThat(lines).containsSubsequence(
            P + "   Verdict " + ".".repeat(26) + " PROBLEMS FOUND: 2 - see FAILED TESTS below",
            P + " FAILED TESTS (2)",
            P + "   X OrderApiTest > rejects empty order : AssertionError: expected 400",
            P + "   X OrderApiTest > (whole class) : IllegalStateException: boom");
    }

    @Test
    @DisplayName("ponad 30 błędów: lista ma pierwsze 30, a resztę podaje liczbą")
    void render_whenMoreThan30Failures_listsFirst30AndCountsRest() {

        // given
        SnapshotBuilder builder = snapshot().withClass(new ClassRecord(ORDERS, 9_000, 4_000, 0, 35, 35, 0));
        IntStream.rangeClosed(1, 35)
            .mapToObj(index -> new FailureRecord(ORDERS, "test-" + index, "AssertionError"))
            .forEach(builder::withFailure);

        // when
        List<String> lines = render(builder.build(), "en", "unit");

        // then
        assertAll(
            () -> assertThat(lines).filteredOn(line -> line.startsWith(P + "   X ")).hasSize(30),
            () -> assertThat(lines).contains(P + "   ... and 5 more"));
    }

    @Test
    @DisplayName("najwolniejsze testy: tylko od sekundy, najdłuższy pierwszy, najwyżej 10")
    void render_listsSlowestTestsLongestFirst() {

        // given
        SnapshotBuilder builder = snapshot().passingClass(ORDERS, 90_000, 80_000, 13).withTiming(ORDERS, "fast", 999);
        IntStream.rangeClosed(1, 12).forEach(index -> builder.withTiming(ORDERS, "test-" + index, index * 1_000L));

        // when
        List<String> lines = render(builder.build(), "en", "unit");

        // then
        List<String> rows = lines.stream().filter(line -> line.contains(" > test-")).toList();
        assertAll(
            () -> assertThat(rows).hasSize(10),
            () -> assertThat(rows.getFirst()).isEqualTo(P + "      12.0 s  OrderApiTest > test-12"),
            () -> assertThat(lines).noneMatch(line -> line.contains("> fast")));
    }

    @Test
    @DisplayName("bez testów dłuższych niż sekunda raport mówi to wprost")
    void render_whenNoSlowTests_saysSo() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 900, 800, 2).withTiming(ORDERS, "fast", 400).build();

        // when
        List<String> lines = render(snapshot, "en", "unit");

        // then
        assertThat(lines).contains(P + "   No single test took longer than 1 second.");
    }

    @Test
    @DisplayName("podpowiedź przy klasie: środowisko, wolne testy albo przygotowanie i sprzątanie")
    void render_slowestClasses_hintWhy() {

        // given
        TestRunSnapshot snapshot = snapshot()
            .withClass(new ClassRecord("com.example.DeltaTest", 1_000, 900, 0, 3, 0, 0))
            .withClass(new ClassRecord("com.example.AlphaTest", 10_000, 3_000, 6_000, 3, 0, 0))
            .withClass(new ClassRecord("com.example.GammaTest", 7_000, 1_000, 0, 3, 0, 0))
            .withClass(new ClassRecord("com.example.BetaTest", 8_000, 6_000, 0, 2, 0, 0))
            .build();

        // when
        List<String> lines = render(snapshot, "en", "unit");

        // then
        List<String> rows = lines.stream().filter(line -> line.matches(".*(Alpha|Beta|Gamma|Delta)Test.*")).toList();
        assertAll(
            () -> assertThat(rows).hasSize(4),
            () -> assertThat(rows.get(0)).contains("AlphaTest").endsWith("starts a test environment"),
            () -> assertThat(rows.get(1)).contains("BetaTest").endsWith("tests are slow - check for waiting/timeouts"),
            () -> assertThat(rows.get(2)).contains("GammaTest").endsWith("slow class setup or cleanup"),
            () -> assertThat(rows.get(3)).contains("DeltaTest").endsWith(" "));
    }

    @Test
    @DisplayName("wiersz czasu: czas, pasek z 20 znaków i procent przebiegu")
    void render_timeRows_showDurationBarAndShare() {

        // given
        TestRunSnapshot snapshot = snapshot()
            .passingClass(ORDERS, 9_000, 4_000, 3)
            .withEnvironment(EnvironmentStart.started(ORDERS, 3_000, List.of("test"), 300, EnvironmentCause.first()))
            .build();

        // when
        List<String> lines = render(snapshot, "en", "unit");

        // then
        assertAll(
            () -> assertThat(lines).contains(P + "   Starting test environments " + ".".repeat(7)
                + " 3.0 s" + " ".repeat(7) + " ######" + ".".repeat(14) + "  30%"),
            () -> assertThat(lines).noneMatch(line -> line.contains("Resuming paused environments")));
    }

    @Test
    @DisplayName("wznowienia środowisk mają w podziale czasu własny wiersz")
    void render_whenResumes_addsResumeTimeRow() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 9_000, 4_000, 3).withResumes(2, 1_000).build();

        // when
        List<String> lines = render(snapshot, "en", "unit");

        // then
        assertThat(lines).contains(P + "   Resuming paused environments " + ".".repeat(5)
            + " 1.0 s" + " ".repeat(7) + " ##" + ".".repeat(18) + "  10%");
    }

    @Test
    @DisplayName("linia DATA ma stałe klucze, wartości w sekundach i identyfikator przebiegu bez spacji")
    void render_dataLine_hasStableKeys() {

        // given
        TestRunSnapshot snapshot = snapshot()
            .withClass(new ClassRecord(ORDERS, 9_000, 4_000, 3_500, 3, 1, 1))
            .withEnvironment(EnvironmentStart.started(ORDERS, 3_000, List.of("test"), 300, EnvironmentCause.first()))
            .withResumes(2, 500)
            .build();

        // when
        List<String> lines = render(snapshot, "pl", "nightly run");

        // then
        assertThat(lines.subList(lines.size() - 2, lines.size())).containsExactly(
            P + """
                DATA label=nightly_run tests=3 failed=1 skipped=1 class_failures=0 classes=1 wall_s=10 \
                env_count=1 env_s=3 env_reloaded=0 env_resumed=2 env_resume_s=0 test_s=4 other_s=2 \
                heap_max_mb=1024 heap_peak_mb=400 heap_live_mb=200 nonheap_mb=120 gc_s=0 full_gc=0""",
            P + "=".repeat(78));
    }

    @Test
    @DisplayName("po polsku: nagłówek, sekcje i werdykt z polskiego pliku tekstów")
    void render_inPolish_translatesSectionsAndLabel() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 9_000, 4_000, 3).build();

        // when
        List<String> lines = render(snapshot, "pl", "unit");

        // then
        assertThat(lines).containsSubsequence(
            P + " RAPORT Z TESTÓW: TESTY JEDNOSTKOWE",
            P + " WYNIK",
            P + "   Werdykt " + ".".repeat(26) + " WSZYSTKIE TESTY PRZESZŁY",
            P + "   Uruchomione testy " + ".".repeat(16) + " 3  (udane 3, z błędem 0, pominięte 0)",
            P + " GDZIE POSZEDŁ CZAS",
            P + " ŚRODOWISKA TESTOWE",
            P + " NAJWOLNIEJSZE KLASY TESTÓW (pierwsze 15)",
            P + " PAMIĘĆ");
    }

    @Test
    @DisplayName("nieznana etykieta przebiegu trafia do nagłówka bez tłumaczenia")
    void render_whenUnknownLabel_showsItLiterally() {

        // given
        TestRunSnapshot snapshot = snapshot().build();

        // when
        List<String> lines = render(snapshot, "en", "nightly");

        // then
        assertThat(lines).contains(P + " TEST REPORT: NIGHTLY");
    }

    @Test
    @DisplayName("wydruk to te same linie co render, każda w osobnym wierszu")
    void print_writesRenderedLines() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 9_000, 4_000, 3).build();
        TestRunReport report = new TestRunReport(ReportMessages.forLanguage("en"));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // when
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {

            report.print(snapshot, "unit", out);
        }

        // then
        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertThat(printed.lines().toList()).isEqualTo(report.render(snapshot, "unit"));
    }

    private static List<String> render(TestRunSnapshot snapshot, String language, String label) {

        TestRunReport report = new TestRunReport(ReportMessages.forLanguage(language));
        return report.render(snapshot, label);
    }
}
