package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.AttributeDifference;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pgoogol.testdiagnostics.report.SnapshotBuilder.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

/** Powody nowych środowisk pod tabelą i sekcja najczęstszych przyczyn. */
class CauseReportTest {

    private static final String P = ReportLines.PREFIX;

    /** Wcięcie raportu i wcięcie powodu pod kolumnę czasu startu. */
    private static final String CAUSE = P + "   " + "       ";

    private static final String ORDERS = "com.example.order.OrderApiTest";

    private static final String PAYMENTS = "com.example.payment.PaymentApiTest";

    private static final AttributeDifference MOCK_SWAPPED = new AttributeDifference(
        "beanOverrides", List.of("@MockitoBean Mailer mailer"), List.of("@MockitoBean Clock clock"));

    private static final AttributeDifference PROFILE_ADDED = new AttributeDifference(
        "profiles", List.of("slow"), List.of());

    @Test
    @DisplayName("pod każdym środowiskiem jego powód; przy różnicy linia na atrybut z dodanymi i usuniętymi wartościami")
    void environments_showCauseUnderEachRow() {

        // given
        TestRunSnapshot snapshot = snapshotWith(
            EnvironmentCause.first(),
            new EnvironmentCause.Differs(1, List.of(MOCK_SWAPPED)));

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertThat(lines).containsSubsequence(
            CAUSE + "reason: first environment in this run",
            CAUSE + "reason: differs from #1 in:",
            CAUSE + "  bean overrides (@MockitoBean): + @MockitoBean Mailer mailer  - @MockitoBean Clock clock");
    }

    @Test
    @DisplayName("ta sama konfiguracja: klasa z @DirtiesContext bez wspólnego pakietu albo limit pamięci podręcznej")
    void environments_showReloadCause() {

        // given
        TestRunSnapshot snapshot = snapshotWith(
            EnvironmentCause.first(),
            new EnvironmentCause.Reloaded(1, PAYMENTS),
            new EnvironmentCause.Reloaded(1, ""));

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertThat(lines).containsSubsequence(
            CAUSE + "reason: same configuration as #1, closed by @DirtiesContext after payment.PaymentApiTest",
            CAUSE + "reason: same configuration as #1, evicted from the context cache"
                + " (spring.test.context.cache.maxSize)");
    }

    @Test
    @DisplayName("konfiguracja, której nie dało się odczytać: powód nieznany z opisem błędu")
    void environments_showUnknownCause() {

        // given
        TestRunSnapshot snapshot = snapshotWith(new EnvironmentCause.Unknown("IllegalStateException: boom"));

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertThat(lines).contains(CAUSE + "reason: unknown, the configuration could not be read (IllegalStateException: boom)");
    }

    @Test
    @DisplayName("najczęstsze przyczyny: liczba środowisk na atrybut i na @DirtiesContext, od największej")
    void causeSummary_countsReasonsMostCommonFirst() {

        // given
        TestRunSnapshot snapshot = snapshotWith(
            EnvironmentCause.first(),
            new EnvironmentCause.Differs(1, List.of(MOCK_SWAPPED, PROFILE_ADDED)),
            new EnvironmentCause.Differs(1, List.of(MOCK_SWAPPED)),
            new EnvironmentCause.Reloaded(1, PAYMENTS));

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertThat(lines).containsSubsequence(
            P + " MOST COMMON REASONS FOR NEW ENVIRONMENTS",
            P + "   bean overrides (@MockitoBean) " + ".".repeat(4) + " 2",
            P + "   profiles " + ".".repeat(25) + " 1",
            P + "   closed by @DirtiesContext " + ".".repeat(8) + " 1");
    }

    @Test
    @DisplayName("jedno środowisko nie ma przyczyny do usunięcia, więc sekcji przyczyn nie ma")
    void causeSummary_whenOnlyFirstEnvironment_isAbsent() {

        // given
        TestRunSnapshot snapshot = snapshotWith(EnvironmentCause.first());

        // when
        List<String> lines = render(snapshot, "en");

        // then
        assertThat(lines).noneMatch(line -> line.contains("MOST COMMON REASONS"));
    }

    @Test
    @DisplayName("po polsku powód i nazwa atrybutu z polskiego pliku tekstów")
    void environments_inPolish_translateCause() {

        // given
        TestRunSnapshot snapshot = snapshotWith(
            EnvironmentCause.first(),
            new EnvironmentCause.Differs(1, List.of(PROFILE_ADDED)));

        // when
        List<String> lines = render(snapshot, "pl");

        // then
        assertThat(lines).containsSubsequence(
            CAUSE + "powód: pierwsze środowisko w tym przebiegu",
            CAUSE + "powód: inna niż #1:",
            CAUSE + "  profile: + slow",
            P + " NAJCZĘSTSZE PRZYCZYNY NOWYCH ŚRODOWISK");
    }

    private static TestRunSnapshot snapshotWith(EnvironmentCause... causes) {

        SnapshotBuilder builder = snapshot()
            .passingClass(ORDERS, 20_000, 4_000, 3)
            .passingClass(PAYMENTS, 9_000, 2_000, 2);
        List.of(causes).forEach(cause -> builder.withEnvironment(EnvironmentStart.started(ORDERS, 3_000, List.of(), 100, cause)));
        return builder.build();
    }

    private static List<String> render(TestRunSnapshot snapshot, String language) {

        TestRunReport report = new TestRunReport(ReportMessages.forLanguage(language));
        return report.render(snapshot, "unit");
    }
}
