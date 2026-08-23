package com.pgoogol.finance.api;

import com.pgoogol.finance.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etap 5: raporty analityczne i pulpit. Daty liczone są od dzisiaj, bo prognoza
 * i terminarz z natury odnoszą się do bieżącej chwili — test na sztywnych
 * datach przestałby cokolwiek sprawdzać po pierwszym miesiącu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class AnalyticsReportIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final YearMonth THIS_MONTH = YearMonth.from(TODAY);
    private static final YearMonth LAST_MONTH = THIS_MONTH.minusMonths(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long currentAccount;
    private long foreignAccount;
    private long food;
    private long housing;

    @BeforeEach
    void prepareData() throws Exception {

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                truncate table finance.scheduled_occurrence, finance.recurring_rule, \
                finance.transaction, finance.import_row, finance.import_batch, \
                finance.account, finance.category restart identity cascade""");
            statement.execute("delete from finance.exchange_rate");
        }
        currentAccount = createAccount("Bieżące", "PLN");
        foreignAccount = createAccount("Walutowe", "EUR");
        food = createCategory("Jedzenie", "EXPENSE");
        housing = createCategory("Mieszkanie", "EXPENSE");

        expense(LAST_MONTH.atDay(5), 10_000L, food, "Biedronka");
        expense(THIS_MONTH.atDay(4), 15_000L, food, "Biedronka");
        expense(THIS_MONTH.atDay(6), 40_000L, housing, "Wspólnota");
    }

    @Test
    @DisplayName("porównanie zestawia bieżący okres z poprzednim oknem tej samej długości")
    void comparison_setsCurrentPeriodAgainstPreviousWindow() throws Exception {

        // given & when
        String url = "/finance/api/v1/reports/comparison?from=%s&to=%s"
            .formatted(THIS_MONTH.atDay(1), THIS_MONTH.atEndOfMonth());
        JsonNode report = objectMapper.readTree(body(url));

        // then: jedzenie 150 zł teraz wobec 100 zł poprzednio to +50%
        JsonNode row = categoryRow(report.get("rows"), food);
        assertAll(
            () -> assertThat(row.get("currentMinor").asLong()).isEqualTo(15_000L),
            () -> assertThat(row.get("previousMinor").asLong()).isEqualTo(10_000L),
            () -> assertThat(row.get("changePercent").asText()).isEqualTo("50.00"),
            () -> assertThat(report.get("previousTo").asText())
                .isEqualTo(THIS_MONTH.atDay(1).minusDays(1).toString()));
    }

    @Test
    @DisplayName("porównanie gdy poprzedni okres był zerowy, nie podaje procentu")
    void comparison_whenPreviousPeriodWasZero_reportsNoPercent() throws Exception {

        // given: mieszkanie pojawia się dopiero w tym miesiącu
        String url = "/finance/api/v1/reports/comparison?from=%s&to=%s"
            .formatted(THIS_MONTH.atDay(1), THIS_MONTH.atEndOfMonth());

        // when
        JsonNode report = objectMapper.readTree(body(url));

        // then: wzrost z zera nie ma procentu — null zamiast nieskończoności
        JsonNode row = categoryRow(report.get("rows"), housing);
        assertThat(row.get("changePercent").isNull()).isTrue();
    }

    @Test
    @DisplayName("zestawienie największych wydatków porządkuje malejąco i grupuje kontrahentów")
    void topSpend_ordersDescendingAndGroupsCounterparties() throws Exception {

        // given & when
        String url = "/finance/api/v1/reports/top-spend?from=%s&to=%s"
            .formatted(LAST_MONTH.atDay(1), THIS_MONTH.atEndOfMonth());
        JsonNode report = objectMapper.readTree(body(url));

        // then
        JsonNode transactions = report.get("transactions");
        JsonNode counterparties = report.get("counterparties");
        assertAll(
            () -> assertThat(transactions.get(0).get("amountMinor").asLong()).isEqualTo(40_000L),
            () -> assertThat(transactions.get(0).get("counterparty").asText())
                .isEqualTo("Wspólnota"),
            () -> assertThat(counterparties.get(0).get("counterparty").asText())
                .isEqualTo("Wspólnota"),
            () -> assertThat(counterparties.get(1).get("counterparty").asText())
                .isEqualTo("Biedronka"),
            () -> assertThat(counterparties.get(1).get("transactionCount").asInt()).isEqualTo(2));
    }

    @Test
    @DisplayName("koszt stały to wyłącznie wydatek powiązany z pozycją terminarza")
    void fixedVsVariable_countsOnlyOccurrenceBackedExpenses() throws Exception {

        // given: rachunek cykliczny zapłacony przez terminarz
        long rule = createRule(housing, 40_000L, LAST_MONTH.atDay(1));
        long occurrence = firstOccurrence(rule);
        pay(occurrence, 42_000L);

        // when
        String url = "/finance/api/v1/reports/fixed-vs-variable?from=%s&to=%s"
            .formatted(LAST_MONTH.atDay(1), THIS_MONTH.atEndOfMonth());
        JsonNode report = objectMapper.readTree(body(url));

        // then: tylko płatność z terminarza jest kosztem stałym
        JsonNode row = periodRow(report.get("rows"), THIS_MONTH.atDay(1).toString());
        assertAll(
            () -> assertThat(row.get("fixedMinor").asLong()).isEqualTo(42_000L),
            () -> assertThat(row.get("variableMinor").asLong()).isEqualTo(55_000L),
            () -> assertThat(row.get("fixedSharePercent").asText()).isEqualTo("43.30"));
    }

    @Test
    @DisplayName("zobowiązania rozdzielają przeterminowane od nadchodzących")
    void upcoming_separatesOverdueFromAhead() throws Exception {

        // given: reguła sprzed dwóch miesięcy ma zaległości
        createRule(housing, 40_000L, THIS_MONTH.minusMonths(2).atDay(1));

        // when
        JsonNode report = objectMapper.readTree(body("/finance/api/v1/reports/upcoming"));

        // then
        assertThat(report.get("overdue")).isNotEmpty();
        report.get("overdue").forEach(item -> {

            assertThat(item.get("overdue").asBoolean()).isTrue();
            assertThat(LocalDate.parse(item.get("dueDate").asText())).isBefore(TODAY);
        });
        report.get("upcoming").forEach(item ->
            assertThat(item.get("overdue").asBoolean()).isFalse());
    }

    @Test
    @DisplayName("prognoza zaczyna od dzisiejszego salda i odejmuje zaplanowane płatności")
    void forecast_startsFromTodayBalanceAndSubtractsScheduledPayments() throws Exception {

        // given: konto PLN ma dziś −65 000, a za kilka dni wypada rachunek
        LocalDate dueDate = TODAY.plusDays(5);
        createRule(housing, 30_000L, dueDate);

        // when: prognoza wyłącznie dla konta w walucie bazowej — dla konta
        // walutowego bez kursu nie da się jej policzyć uczciwie
        String url = "/finance/api/v1/reports/forecast?horizonDays=10&accountIds=" + currentAccount;
        JsonNode report = objectMapper.readTree(body(url));

        // then
        JsonNode points = report.get("points");
        assertAll(
            () -> assertThat(report.get("startingBalanceMinor").asLong()).isEqualTo(-65_000L),
            () -> assertThat(points).hasSize(11),
            () -> assertThat(points.get(0).get("date").asText()).isEqualTo(TODAY.toString()),
            () -> assertThat(points.get(10).get("balanceMinor").asLong()).isEqualTo(-95_000L));
    }

    @Test
    @DisplayName("macierz roczna ma dwanaście kolumn i zgodne sumy")
    void yearlyMatrix_hasTwelveColumnsAndConsistentTotals() throws Exception {

        // given & when
        String url = "/finance/api/v1/reports/yearly-matrix?year=" + TODAY.getYear();
        JsonNode report = objectMapper.readTree(body(url));

        // then
        JsonNode rows = report.get("rows");
        assertThat(rows).isNotEmpty();
        rows.forEach(row -> assertThat(row.get("monthsMinor")).hasSize(12));
        assertThat(report.get("monthlyTotalsMinor")).hasSize(12);
        long rowsTotal = 0L;
        for (JsonNode row : rows) {

            rowsTotal = rowsTotal + row.get("totalMinor").asLong();
        }
        assertThat(report.get("totalMinor").asLong()).isEqualTo(rowsTotal);
    }

    @Test
    @DisplayName("ekspozycja walutowa pokazuje saldo bez kursu z pustą wyceną")
    void currencyExposure_showsBalanceWithoutRateAsUnvalued() throws Exception {

        // given: nikt nie pobrał kursu EUR
        transfer(THIS_MONTH.atDay(10), 20_000L, 5_000L);

        // when
        JsonNode report = objectMapper.readTree(body("/finance/api/v1/reports/currency-exposure"));

        // then: euro widać, wyceny nie ma — i to jest lepsze niż pusta strona
        JsonNode euro = currencyRow(report.get("rows"), "EUR");
        JsonNode zloty = currencyRow(report.get("rows"), "PLN");
        assertAll(
            () -> assertThat(euro.get("balanceMinor").asLong()).isEqualTo(5_000L),
            () -> assertThat(euro.get("baseValueMinor").isNull()).isTrue(),
            () -> assertThat(zloty.get("baseValueMinor").asLong())
                .isEqualTo(zloty.get("balanceMinor").asLong()),
            () -> assertThat(report.get("totalBaseMinor").asLong())
                .isEqualTo(zloty.get("balanceMinor").asLong()));
    }

    @Test
    @DisplayName("pulpit zbiera salda, bilans miesiąca i najbliższe płatności")
    void dashboard_gathersBalancesMonthNetAndUpcoming() throws Exception {

        // given & when & then
        mockMvc.perform(get("/finance/api/v1/reports/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(THIS_MONTH.atDay(1).toString()))
            .andExpect(jsonPath("$.monthExpenseMinor").value(55_000))
            .andExpect(jsonPath("$.monthIncomeMinor").value(0))
            .andExpect(jsonPath("$.monthNetMinor").value(-55_000))
            .andExpect(jsonPath("$.accountBalances.length()").value(2));
    }

    private String body(String url) throws Exception {

        return mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private JsonNode categoryRow(JsonNode rows, long categoryId) {

        for (JsonNode row : rows) {

            if (row.get("categoryId").asLong() == categoryId) {

                return row;
            }
        }
        throw new AssertionError("Brak wiersza kategorii " + categoryId);
    }

    private JsonNode periodRow(JsonNode rows, String period) {

        for (JsonNode row : rows) {

            if (row.get("period").asText().equals(period)) {

                return row;
            }
        }
        throw new AssertionError("Brak wiersza okresu " + period);
    }

    private JsonNode currencyRow(JsonNode rows, String currency) {

        for (JsonNode row : rows) {

            if (row.get("currency").asText().equals(currency)) {

                return row;
            }
        }
        throw new AssertionError("Brak wiersza waluty " + currency);
    }

    private long createRule(long categoryId, long amountMinor, LocalDate startsOn)
            throws Exception {

        String body = """
            {"name":"Czynsz","accountId":%d,"categoryId":%d,"type":"EXPENSE",
             "amountMinor":%d,"frequency":"MONTHLY","dayOfMonth":%d,"startsOn":"%s"}"""
            .formatted(currentAccount, categoryId, amountMinor, startsOn.getDayOfMonth(), startsOn);
        String response = mockMvc.perform(post("/finance/api/v1/recurring-rules")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long firstOccurrence(long ruleId) throws Exception {

        JsonNode schedule =
            objectMapper.readTree(body("/finance/api/v1/occurrences?ruleId=" + ruleId));
        return schedule.get(0).get("id").asLong();
    }

    private void pay(long occurrenceId, long paidAmountMinor) throws Exception {

        String body = """
            {"paidOn":"%s","paidAmountMinor":%d}""".formatted(TODAY, paidAmountMinor);
        mockMvc.perform(post("/finance/api/v1/occurrences/{id}/pay", occurrenceId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    private void expense(LocalDate bookedOn, long amountMinor, long categoryId,
                         String counterparty) throws Exception {

        String body = """
            {"type":"EXPENSE","bookedOn":"%s","amountMinor":%d,"currency":"PLN",
             "accountId":%d,"categoryId":%d,"counterparty":"%s"}"""
            .formatted(bookedOn, amountMinor, currentAccount, categoryId, counterparty);
        createTransaction(body);
    }

    private void transfer(LocalDate bookedOn, long amountMinor, long toAmountMinor)
            throws Exception {

        String body = """
            {"type":"TRANSFER","bookedOn":"%s","amountMinor":%d,"currency":"PLN",
             "accountId":%d,"toAccountId":%d,"toAmountMinor":%d}"""
            .formatted(bookedOn, amountMinor, currentAccount, foreignAccount, toAmountMinor);
        createTransaction(body);
    }

    private void createTransaction(String body) throws Exception {

        mockMvc.perform(post("/finance/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private long createAccount(String name, String currency) throws Exception {

        String body = mockMvc.perform(post("/finance/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"BANK","currency":"%s","openingBalanceMinor":0,
                     "openingBalanceOn":"%s"}"""
                    .formatted(name, currency, TODAY.minusYears(2))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createCategory(String name, String direction) throws Exception {

        String body = mockMvc.perform(post("/finance/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","direction":"%s"}""".formatted(name, direction)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}
