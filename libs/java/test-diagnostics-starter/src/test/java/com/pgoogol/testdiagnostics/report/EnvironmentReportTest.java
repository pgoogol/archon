package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static com.pgoogol.testdiagnostics.report.SnapshotBuilder.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class EnvironmentReportTest {

    private static final String P = ReportLines.PREFIX;

    /** Szerokości kolumn tabeli środowisk: numer, start, klasa, profile, beany. */
    private static final String ROW = "%3s  %10s  %-44s %-18s %6s";

    private static final String ORDERS = "com.example.order.OrderApiTest";

    private static final String PAYMENTS = "com.example.payment.PaymentApiTest";

    @Test
    @DisplayName("bez środowisk sekcja mówi, że były same zwykłe testy")
    void write_whenNoEnvironments_saysPlainTestsOnly() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 900, 800, 2).build();

        // when
        List<String> lines = render(snapshot);

        // then
        assertThat(lines).containsSubsequence(
            P + " TEST ENVIRONMENTS",
            P + "   No application environment was started in this part (plain tests only).");
    }

    @Test
    @DisplayName("tabela środowisk: numer, czas startu, klasa bez wspólnego pakietu, profile i beany; nieudany start bez beanów")
    void write_listsEnvironmentsWithFailedStartMarked() {

        // given
        TestRunSnapshot snapshot = snapshot()
            .passingClass(ORDERS, 20_000, 4_000, 3)
            .passingClass(PAYMENTS, 3_000, 0, 1)
            .withEnvironment(EnvironmentStart.started(ORDERS, 14_100, List.of("test", "local"), 412, EnvironmentCause.first()))
            .withEnvironment(EnvironmentStart.failed(PAYMENTS + "$WhenDeclined", 2_300, EnvironmentCause.first()))
            .build();

        // when
        List<String> lines = render(snapshot);

        // then
        assertThat(lines).containsSubsequence(
            P + "   New environments started: 2, total start time: 16.4 s",
            P + "   " + row("#", "Start time", "Started for test class", "Profiles", "Beans"),
            P + "   " + row("1", "14.1 s", "order.OrderApiTest", "test,local", "412"),
            P + "   " + row("2", "FAILED", "payment.PaymentApiTest$WhenDeclined", "-", "-"),
            P + "   The first environment is usually the slowest, because Java is still warming up.");
    }

    @Test
    @DisplayName("ponad 30 środowisk: tabela ma pierwsze 30, a resztę podaje liczbą")
    void write_whenMoreThan30Environments_listsFirst30() {

        // given
        SnapshotBuilder builder = snapshot().passingClass(ORDERS, 90_000, 4_000, 3);
        IntStream.rangeClosed(1, 32)
            .mapToObj(index -> EnvironmentStart.started(ORDERS, 1_000, List.of(), 100, EnvironmentCause.first()))
            .forEach(builder::withEnvironment);

        // when
        List<String> lines = render(builder.build());

        // then
        assertAll(
            () -> assertThat(lines).contains(P + "   " + row("30", "1.0 s", "OrderApiTest", "-", "100")),
            () -> assertThat(lines).noneMatch(line -> line.startsWith(P + "    31  ")),
            () -> assertThat(lines).contains(P + "   ... and 2 more"));
    }

    @Test
    @DisplayName("wznowienia wstrzymanych środowisk: liczba, czas i objaśnienie")
    void write_whenResumes_reportsThem() {

        // given
        TestRunSnapshot snapshot = snapshot().passingClass(ORDERS, 9_000, 4_000, 3).withResumes(3, 1_500).build();

        // when
        List<String> lines = render(snapshot);

        // then
        assertThat(lines).containsSubsequence(
            P + "   Paused environments resumed: 3, total time: 1.5 s",
            P + "   Spring pauses environments that are not in use and restarts their components");
    }

    @Test
    @DisplayName("po polsku nieudany start to BŁĄD")
    void write_inPolish_marksFailedStart() {

        // given
        TestRunSnapshot snapshot = snapshot()
            .passingClass(ORDERS, 20_000, 4_000, 3)
            .withEnvironment(EnvironmentStart.failed(ORDERS, 2_300, EnvironmentCause.first()))
            .build();

        // when
        TestRunReport report = new TestRunReport(ReportMessages.forLanguage("pl"));
        List<String> lines = report.render(snapshot, "unit");

        // then
        assertThat(lines).contains(P + "   " + row("1", "BŁĄD", "OrderApiTest", "-", "-"));
    }

    private static String row(String number, String start, String testClass, String profiles, String beans) {

        return String.format(Locale.ROOT, ROW, number, start, testClass, profiles, beans);
    }

    private static List<String> render(TestRunSnapshot snapshot) {

        TestRunReport report = new TestRunReport(ReportMessages.forLanguage("en"));
        return report.render(snapshot, "unit");
    }
}
