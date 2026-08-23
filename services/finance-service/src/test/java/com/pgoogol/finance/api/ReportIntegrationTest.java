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
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Definicja ukończenia etapu 4: {@code TRANSFER} nie występuje w żadnym
 * raporcie. Sprawdzamy to jednym testem przechodzącym po wszystkich
 * endpointach naraz — po jednym na raport rozjechałoby się przy dwunastym.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ReportIntegrationTest {

    private static final LocalDate OPENED_ON = LocalDate.of(2026, 1, 1);
    private static final String FROM = "2026-01-01";
    private static final String TO = "2026-12-31";

    /** Kwota transferu jest nietypowa, żeby dało się jej szukać w odpowiedzi. */
    private static final long TRANSFER_MINOR = 777_777L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long currentAccount;
    private long foreignAccount;
    private long food;
    private long restaurants;
    private long salary;

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
        food = createCategory("Jedzenie", "EXPENSE", null);
        restaurants = createCategory("Restauracje", "EXPENSE", food);
        salary = createCategory("Wypłata", "INCOME", null);

        expense("2026-03-05", 12_000L, food);
        expense("2026-03-18", 8_000L, restaurants);
        expense("2026-04-02", 5_000L, restaurants);
        income("2026-03-01", 900_000L, salary);
        transfer("2026-03-20", TRANSFER_MINOR, 180_000L);
    }

    @Test
    @DisplayName("żaden raport nie liczy transferu")
    void reports_neverCountTransfers() throws Exception {

        // given: przelew na własne konto policzony jako wydatek zawyżałby naraz
        // wydatki i przychody — to najdroższy błąd w całym module raportów
        List<String> endpoints = List.of(
            "/finance/api/v1/reports/by-category",
            "/finance/api/v1/reports/cashflow");

        // when & then: sprawdzamy komplet w jednej pętli, nie po jednym teście
        for (String endpoint : endpoints) {

            String body = report(endpoint, "");
            assertThat(body)
                .as("raport %s nie może zawierać kwoty transferu", endpoint)
                .doesNotContain(String.valueOf(TRANSFER_MINOR));
        }
        JsonNode cashflow = objectMapper.readTree(report("/finance/api/v1/reports/cashflow", ""));
        JsonNode march = periodRow(cashflow.get("rows"), "2026-03-01");
        assertAll(
            () -> assertThat(march.get("expenseMinor").asLong()).isEqualTo(20_000L),
            () -> assertThat(march.get("incomeMinor").asLong()).isEqualTo(900_000L),
            () -> assertThat(march.get("netMinor").asLong()).isEqualTo(880_000L));
    }

    @Test
    @DisplayName("saldo w raporcie kont uwzględnia transfer po obu stronach")
    void balances_countTransferOnBothSides() throws Exception {

        // given: transfer wypada z zestawień wydatków, ale saldo bez niego
        // byłoby po prostu błędne
        String body = report("/finance/api/v1/reports/balances", "");

        // when
        JsonNode rows = objectMapper.readTree(body).get("rows");
        JsonNode currentAccountMarch = accountRow(rows, "2026-03-01", currentAccount);
        JsonNode foreignAccountMarch = accountRow(rows, "2026-03-01", foreignAccount);

        // then: 900 000 przychodu − 20 000 wydatków − 777 777 transferu
        assertAll(
            () -> assertThat(currentAccountMarch.get("balanceMinor").asLong()).isEqualTo(102_223L),
            () -> assertThat(currentAccountMarch.get("currency").asText()).isEqualTo("PLN"),
            () -> assertThat(foreignAccountMarch.get("balanceMinor").asLong()).isEqualTo(180_000L),
            () -> assertThat(foreignAccountMarch.get("currency").asText()).isEqualTo("EUR"));
    }

    @Test
    @DisplayName("kategoria nadrzędna niesie sumę swoich podkategorii")
    void byCategory_parentCarriesSubcategorySums() throws Exception {

        // given & when
        JsonNode report = objectMapper.readTree(report("/finance/api/v1/reports/by-category", ""));
        JsonNode rows = report.get("rows");
        JsonNode foodMarch = categoryRow(rows, "2026-03-01", food);
        JsonNode restaurantsMarch = categoryRow(rows, "2026-03-01", restaurants);

        // then: rodzic ma 12 000 własne + 8 000 z podkategorii
        assertAll(
            () -> assertThat(foodMarch.get("amountMinor").asLong()).isEqualTo(20_000L),
            () -> assertThat(foodMarch.get("ownAmountMinor").asLong()).isEqualTo(12_000L),
            () -> assertThat(foodMarch.get("transactionCount").asInt()).isEqualTo(2),
            () -> assertThat(restaurantsMarch.get("amountMinor").asLong()).isEqualTo(8_000L),
            () -> assertThat(restaurantsMarch.get("parentCategoryId").asLong())
                .isEqualTo(food));
    }

    @Test
    @DisplayName("suma okresu nie liczy gałęzi dwa razy")
    void byCategory_totalsCountEachTransactionOnce() throws Exception {

        // given & when
        JsonNode report = objectMapper.readTree(report("/finance/api/v1/reports/by-category", ""));
        JsonNode totals = report.get("totals");

        // then: 12 000 + 8 000, a nie 12 000 + 8 000 + 8 000 przez rodzica
        JsonNode march = periodRow(totals, "2026-03-01");
        assertThat(march.get("amountMinor").asLong()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("filtr kategorii obejmuje jej podkategorie")
    void byCategory_categoryFilterIncludesSubcategories() throws Exception {

        // given: filtr po „Jedzeniu" bez podkategorii pokazywałby zero
        // w drzewie, w którym wszystko siedzi w liściach
        String query = "&categoryIds=" + food;

        // when
        JsonNode report =
            objectMapper.readTree(report("/finance/api/v1/reports/by-category", query));

        // then
        JsonNode march = periodRow(report.get("totals"), "2026-03-01");
        assertThat(march.get("amountMinor").asLong()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("podział roczny scala wszystkie miesiące w jeden wiersz")
    void cashflow_yearlyGranularityMergesMonths() throws Exception {

        // given & when
        JsonNode report = objectMapper.readTree(
            report("/finance/api/v1/reports/cashflow", "&granularity=YEAR"));

        // then: marzec i kwiecień razem
        JsonNode rows = report.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("expenseMinor").asLong()).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("odwrócony zakres dat kończy się błędem, nie pustym raportem")
    void report_whenRangeIsReversed_reportsBadRequest() throws Exception {

        // given & when & then
        mockMvc.perform(get("/finance/api/v1/reports/cashflow")
                .param("from", TO).param("to", FROM))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
    }

    private String report(String endpoint, String query) throws Exception {

        String url = "%s?from=%s&to=%s%s".formatted(endpoint, FROM, TO, query);
        return mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private JsonNode periodRow(JsonNode rows, String period) {

        for (JsonNode row : rows) {

            if (row.get("period").asText().equals(period)) {

                return row;
            }
        }
        throw new AssertionError("Brak wiersza dla okresu " + period);
    }

    private JsonNode categoryRow(JsonNode rows, String period, long categoryId) {

        for (JsonNode row : rows) {

            boolean matches = row.get("period").asText().equals(period)
                && row.get("categoryId").asLong() == categoryId;
            if (matches) {

                return row;
            }
        }
        throw new AssertionError("Brak wiersza kategorii " + categoryId);
    }

    private JsonNode accountRow(JsonNode rows, String period, long accountId) {

        for (JsonNode row : rows) {

            boolean matches = row.get("period").asText().equals(period)
                && row.get("accountId").asLong() == accountId;
            if (matches) {

                return row;
            }
        }
        throw new AssertionError("Brak wiersza konta " + accountId);
    }

    private void expense(String bookedOn, long amountMinor, long categoryId) throws Exception {

        String body = """
            {"type":"EXPENSE","bookedOn":"%s","amountMinor":%d,"currency":"PLN",
             "accountId":%d,"categoryId":%d}"""
            .formatted(bookedOn, amountMinor, currentAccount, categoryId);
        createTransaction(body);
    }

    private void income(String bookedOn, long amountMinor, long categoryId) throws Exception {

        String body = """
            {"type":"INCOME","bookedOn":"%s","amountMinor":%d,"currency":"PLN",
             "accountId":%d,"categoryId":%d}"""
            .formatted(bookedOn, amountMinor, currentAccount, categoryId);
        createTransaction(body);
    }

    private void transfer(String bookedOn, long amountMinor, long toAmountMinor) throws Exception {

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
                     "openingBalanceOn":"%s"}""".formatted(name, currency, OPENED_ON)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createCategory(String name, String direction, Long parentId) throws Exception {

        String parent = "null";
        if (Objects.nonNull(parentId)) {

            parent = String.valueOf(parentId);
        }
        String body = mockMvc.perform(post("/finance/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","direction":"%s","parentId":%s}"""
                    .formatted(name, direction, parent)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}
