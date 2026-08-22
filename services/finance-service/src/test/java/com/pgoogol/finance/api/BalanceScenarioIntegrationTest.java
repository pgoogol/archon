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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Definicja ukończenia etapu 1: saldo zgadza się dla scenariusza
 * wydatek + przychód + transfer, także między kontami w różnych walutach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class BalanceScenarioIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Test przez pełny stos HTTP nie jest transakcyjny — każdy zapis commituje się
     * na stałe. Kontener bazy jest jeden na cały przebieg, więc bez czyszczenia
     * kolejny test (i kolejna klasa) zaczynałby na cudzych danych.
     */
    @BeforeEach
    void clearFinanceTables() throws Exception {

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                truncate table finance.transaction, finance.account, finance.category \
                restart identity cascade""");
            statement.execute("delete from finance.exchange_rate");
        }
    }

    @Test
    @DisplayName("saldo obu kont zgadza się po wydatku, przychodzie i transferze PLN → EUR")
    void balance_afterExpenseIncomeAndCrossCurrencyTransfer_matchesBookedAmounts() throws Exception {

        // given
        addEuroRate("4.00000000");
        long zlotyAccount = createAccount("Bieżące", "PLN");
        long euroAccount = createAccount("Walutowe", "EUR");
        long salary = createCategory("Wynagrodzenie", "INCOME");
        long food = createCategory("Jedzenie", "EXPENSE");

        // when: 5000,00 wpływu, 200,00 wydatku, 1000,00 przelane na konto walutowe
        // jako 230,00 EUR
        createTransaction("""
            {"type":"INCOME","bookedOn":"%s","amountMinor":500000,"currency":"PLN",
             "accountId":%d,"categoryId":%d}""".formatted(TODAY, zlotyAccount, salary));
        createTransaction("""
            {"type":"EXPENSE","bookedOn":"%s","amountMinor":20000,"currency":"PLN",
             "accountId":%d,"categoryId":%d}""".formatted(TODAY, zlotyAccount, food));
        createTransaction("""
            {"type":"TRANSFER","bookedOn":"%s","amountMinor":100000,"currency":"PLN",
             "accountId":%d,"toAccountId":%d,"toAmountMinor":23000}"""
            .formatted(TODAY, zlotyAccount, euroAccount));

        // then: 5000 − 200 − 1000 = 3800,00 zł oraz 230,00 EUR po kursie 4,00 = 920,00 zł
        mockMvc.perform(get("/api/finance/accounts/{id}/balance", zlotyAccount))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceMinor").value(380000))
            .andExpect(jsonPath("$.baseBalanceMinor").value(380000));

        mockMvc.perform(get("/api/finance/accounts/{id}/balance", euroAccount))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceMinor").value(23000))
            .andExpect(jsonPath("$.baseBalanceMinor").value(92000));
    }

    @Test
    @DisplayName("transakcja w walucie obcej zapisuje kurs i kwotę bazową w chwili powstania")
    void createTransaction_whenCurrencyIsForeign_storesHistoricalRate() throws Exception {

        // given
        addEuroRate("4.00000000");
        long euroAccount = createAccount("Walutowe EUR", "EUR");
        long food = createCategory("Restauracje", "EXPENSE");

        // when: 100,00 EUR
        JsonNode created = createTransaction("""
            {"type":"EXPENSE","bookedOn":"%s","amountMinor":10000,"currency":"EUR",
             "accountId":%d,"categoryId":%d}""".formatted(TODAY, euroAccount, food));

        // then
        assertThat(created.get("baseAmountMinor").asLong()).isEqualTo(40_000L);
        assertThat(created.get("fxRate").asString()).isEqualTo("4.00000000");
        assertThat(created.get("baseCurrency").asString()).isEqualTo("PLN");
    }

    @Test
    @DisplayName("transfer z kategorią jest odrzucany komunikatem, nie błędem bazy")
    void createTransaction_whenTransferHasCategory_returnsBadRequest() throws Exception {

        // given
        long zlotyAccount = createAccount("Bieżące PLN", "PLN");
        long otherAccount = createAccount("Oszczędnościowe PLN", "PLN");
        long food = createCategory("Zakupy", "EXPENSE");

        // when & then
        mockMvc.perform(post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TRANSFER","bookedOn":"%s","amountMinor":10000,"currency":"PLN",
                     "accountId":%d,"toAccountId":%d,"categoryId":%d}"""
                    .formatted(TODAY, zlotyAccount, otherAccount, food)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("TRANSFER_WITH_CATEGORY"));
    }

    @Test
    @DisplayName("wydatek bez kategorii jest odrzucany komunikatem, nie błędem bazy")
    void createTransaction_whenExpenseHasNoCategory_returnsBadRequest() throws Exception {

        // given
        long zlotyAccount = createAccount("Bieżące bez kategorii", "PLN");

        // when & then
        mockMvc.perform(post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"EXPENSE","bookedOn":"%s","amountMinor":10000,"currency":"PLN",
                     "accountId":%d}""".formatted(TODAY, zlotyAccount)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("CATEGORY_REQUIRED"));
    }

    private void addEuroRate(String rate) throws Exception {

        mockMvc.perform(post("/api/finance/exchange-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"EUR","rateDate":"%s","rate":"%s"}""".formatted(TODAY, rate)))
            .andExpect(status().isCreated());
    }

    private long createAccount(String name, String currency) throws Exception {

        String body = mockMvc.perform(post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"BANK","currency":"%s","openingBalanceMinor":0,
                     "openingBalanceOn":"%s"}""".formatted(name, currency, TODAY.minusYears(1))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createCategory(String name, String direction) throws Exception {

        String body = mockMvc.perform(post("/api/finance/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","direction":"%s"}""".formatted(name, direction)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private JsonNode createTransaction(String body) throws Exception {

        String response = mockMvc.perform(post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
