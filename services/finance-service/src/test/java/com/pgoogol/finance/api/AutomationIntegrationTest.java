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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etap 6: automatyzacja importu. Każda podpowiedź musi dać się potwierdzić
 * i żadna nie może zmienić danych sama z siebie — to jest cała treść tych
 * testów.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class AutomationIntegrationTest {

    private static final Charset WINDOWS_1250 = Charset.forName("windows-1250");
    private static final LocalDate TERMIN = LocalDate.of(2026, 2, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long biezace;
    private long oszczednosciowe;
    private long jedzenie;
    private long media;

    @BeforeEach
    void prepareData() throws Exception {

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                truncate table finance.category_rule, finance.scheduled_occurrence, \
                finance.recurring_rule, finance.transaction, finance.import_row, \
                finance.import_batch, finance.account, finance.category restart identity \
                cascade""");
            statement.execute("delete from finance.exchange_rate");
        }
        biezace = createAccount("Bieżące", "PLN");
        oszczednosciowe = createAccount("Oszczędnościowe", "PLN");
        jedzenie = createCategory("Jedzenie", "EXPENSE");
        media = createCategory("Media", "EXPENSE");
    }

    @Test
    @DisplayName("reguła kategoryzacji podpowiada kategorię, ale jej nie zapisuje")
    void categoryRule_suggestsCategoryWithoutSavingIt() throws Exception {

        // given
        createCategoryRule("biedronka", "ANY", jedzenie);
        byte[] wyciag = statement(row("2026-02-05", "-45,00", "ZAKUP BIEDRONKA 1234", ""));

        // when
        long batch = upload(biezace, "luty.csv", wyciag);
        JsonNode podglad = preview(batch);

        // then: podpowiedź jest, transakcji nie ma
        JsonNode wiersz = podglad.get("rows").get(0);
        assertAll(
            () -> assertThat(wiersz.get("suggestedCategoryId").asLong()).isEqualTo(jedzenie),
            () -> assertThat(wiersz.get("suggestedCategoryName").asText()).isEqualTo("Jedzenie"),
            () -> assertThat(wiersz.get("transactionId").isNull()).isTrue());
    }

    @Test
    @DisplayName("poprawka użytkownika przy zatwierdzeniu zapamiętuje nową regułę")
    void commit_whenUserProvidesPattern_remembersRule() throws Exception {

        // given
        long batch = upload(biezace, "luty.csv",
            statement(row("2026-02-05", "-45,00", "ZAKUP ZABKA 77", "")));
        long rowId = preview(batch).get("rows").get(0).get("id").asLong();

        // when
        String body = """
            {"categoryAssignments":[
              {"rowId":%d,"categoryId":%d,"rememberPattern":"ZABKA"}]}"""
            .formatted(rowId, jedzenie);
        mockMvc.perform(post("/finance/api/v1/imports/{id}/commit", batch)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        // then
        JsonNode reguly = objectMapper.readTree(body("/finance/api/v1/category-rules"));
        assertThat(reguly).hasSize(1);
        assertAll(
            () -> assertThat(reguly.get(0).get("pattern").asText()).isEqualTo("ZABKA"),
            () -> assertThat(reguly.get(0).get("categoryId").asLong()).isEqualTo(jedzenie));
    }

    @Test
    @DisplayName("wiersz o kwocie w granicach 10% i terminie w oknie dostaje sugestię rachunku")
    void upload_whenRowMatchesSchedule_suggestsOccurrence() throws Exception {

        // given: rachunek na 100 zł z terminem 10 lutego
        long rule = createRule("Prąd", media, 10_000L, TERMIN, "PRAD");
        long occurrence = occurrenceOn(rule, TERMIN);

        // when: wyciąg pokazuje 105 zł zaksięgowane trzy dni wcześniej
        long batch = upload(biezace, "luty.csv",
            statement(row("2026-02-07", "-105,00", "OPLATA ZA PRAD", "")));

        // then
        JsonNode wiersz = preview(batch).get("rows").get(0);
        assertAll(
            () -> assertThat(wiersz.get("suggestedOccurrenceId").asLong()).isEqualTo(occurrence),
            () -> assertThat(wiersz.get("suggestedOccurrenceName").asText()).isEqualTo("Prąd"));
        // sama sugestia niczego nie rozliczyła
        assertThat(occurrenceStatus(occurrence)).isEqualTo("OVERDUE");
    }

    @Test
    @DisplayName("wiersz o kwocie różniącej się o 20% nie dostaje sugestii rachunku")
    void upload_whenAmountDiffersTooMuch_suggestsNoOccurrence() throws Exception {

        // given
        createRule("Prąd", media, 10_000L, TERMIN, "PRAD");

        // when: 120 zł zamiast 100 zł
        long batch = upload(biezace, "luty.csv",
            statement(row("2026-02-09", "-120,00", "OPLATA ZA PRAD", "")));

        // then
        JsonNode wiersz = preview(batch).get("rows").get(0);
        assertThat(wiersz.get("suggestedOccurrenceId").isNull()).isTrue();
    }

    @Test
    @DisplayName("potwierdzone rozliczenie zamyka pozycję terminarza transakcją z wyciągu")
    void commit_whenOccurrenceConfirmed_settlesItWithImportedTransaction() throws Exception {

        // given
        long rule = createRule("Prąd", media, 10_000L, TERMIN, "PRAD");
        long occurrence = occurrenceOn(rule, TERMIN);
        long batch = upload(biezace, "luty.csv",
            statement(row("2026-02-09", "-105,00", "OPLATA ZA PRAD", "")));
        long rowId = preview(batch).get("rows").get(0).get("id").asLong();

        // when
        String body = """
            {"categoryAssignments":[{"rowId":%d,"categoryId":%d}],
             "occurrenceAssignments":[{"rowId":%d,"occurrenceId":%d}]}"""
            .formatted(rowId, media, rowId, occurrence);
        mockMvc.perform(post("/finance/api/v1/imports/{id}/commit", batch)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        // then: rachunek zapłacony faktyczną kwotą z wyciągu, nie oczekiwaną
        JsonNode pozycja = occurrence(occurrence);
        assertAll(
            () -> assertThat(pozycja.get("status").asText()).isEqualTo("PAID"),
            () -> assertThat(pozycja.get("paidAmountMinor").asLong()).isEqualTo(10_500L),
            () -> assertThat(pozycja.get("transactionId").isNull()).isFalse());
    }

    @Test
    @DisplayName("para wydatek plus wpływ na dwóch kontach daje propozycję przelewu")
    void transferCandidates_proposeMergeForMatchingPair() throws Exception {

        // given
        long wydatek = createExpense(biezace, "2026-02-10", 50_000L, jedzenie);
        long wplyw = createIncome(oszczednosciowe, "2026-02-12", 50_000L);

        // when
        String url = "/finance/api/v1/transfers/candidates?from=2026-02-01&to=2026-02-28";
        JsonNode propozycje = objectMapper.readTree(body(url));

        // then
        assertThat(propozycje).hasSize(1);
        assertAll(
            () -> assertThat(propozycje.get(0).get("expenseTransactionId").asLong())
                .isEqualTo(wydatek),
            () -> assertThat(propozycje.get(0).get("incomeTransactionId").asLong())
                .isEqualTo(wplyw),
            () -> assertThat(propozycje.get(0).get("daysApart").asLong()).isEqualTo(2L));
    }

    @Test
    @DisplayName("scalenie potwierdzonej pary zostawia jedną transakcję typu TRANSFER")
    void mergeTransfer_leavesSingleTransferTransaction() throws Exception {

        // given
        long wydatek = createExpense(biezace, "2026-02-10", 50_000L, jedzenie);
        long wplyw = createIncome(oszczednosciowe, "2026-02-12", 50_000L);

        // when
        String body = """
            {"expenseTransactionId":%d,"incomeTransactionId":%d}""".formatted(wydatek, wplyw);
        mockMvc.perform(post("/finance/api/v1/transfers/merge")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("TRANSFER"))
            .andExpect(jsonPath("$.amountMinor").value(50_000));

        // then: obie strony zniknęły, została jedna operacja
        mockMvc.perform(get("/finance/api/v1/transactions/{id}", wydatek))
            .andExpect(status().isNotFound());
        JsonNode lista = objectMapper.readTree(body("/finance/api/v1/transactions"));
        assertThat(lista.get("totalElements").asInt()).isEqualTo(1);
    }

    private String occurrenceStatus(long occurrenceId) throws Exception {

        return occurrence(occurrenceId).get("status").asText();
    }

    private JsonNode occurrence(long occurrenceId) throws Exception {

        JsonNode terminarz = objectMapper.readTree(body("/finance/api/v1/occurrences"));
        for (JsonNode item : terminarz) {

            if (item.get("id").asLong() == occurrenceId) {

                return item;
            }
        }
        throw new AssertionError("Brak pozycji terminarza " + occurrenceId);
    }

    private long occurrenceOn(long ruleId, LocalDate dueDate) throws Exception {

        JsonNode terminarz =
            objectMapper.readTree(body("/finance/api/v1/occurrences?ruleId=" + ruleId));
        for (JsonNode item : terminarz) {

            if (item.get("dueDate").asText().equals(dueDate.toString())) {

                return item.get("id").asLong();
            }
        }
        throw new AssertionError("Brak pozycji terminarza na " + dueDate);
    }

    private long createRule(String name, long categoryId, long amountMinor, LocalDate startsOn,
                            String matchPattern) throws Exception {

        String body = """
            {"name":"%s","accountId":%d,"categoryId":%d,"type":"EXPENSE","amountMinor":%d,
             "frequency":"MONTHLY","dayOfMonth":%d,"startsOn":"%s","matchPattern":"%s"}"""
            .formatted(name, biezace, categoryId, amountMinor, startsOn.getDayOfMonth(),
                startsOn, matchPattern);
        String response = mockMvc.perform(post("/finance/api/v1/recurring-rules")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createCategoryRule(String pattern, String matchField, long categoryId)
            throws Exception {

        String body = """
            {"pattern":"%s","matchField":"%s","categoryId":%d}"""
            .formatted(pattern, matchField, categoryId);
        mockMvc.perform(post("/finance/api/v1/category-rules")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private long createExpense(long accountId, String bookedOn, long amountMinor, long categoryId)
            throws Exception {

        String body = """
            {"type":"EXPENSE","bookedOn":"%s","amountMinor":%d,"currency":"PLN",
             "accountId":%d,"categoryId":%d}"""
            .formatted(bookedOn, amountMinor, accountId, categoryId);
        return createTransaction(body);
    }

    private long createIncome(long accountId, String bookedOn, long amountMinor) throws Exception {

        long przychod = createCategory("Zwroty", "INCOME");
        String body = """
            {"type":"INCOME","bookedOn":"%s","amountMinor":%d,"currency":"PLN",
             "accountId":%d,"categoryId":%d}"""
            .formatted(bookedOn, amountMinor, accountId, przychod);
        return createTransaction(body);
    }

    private long createTransaction(String body) throws Exception {

        String response = mockMvc.perform(post("/finance/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long upload(long accountId, String fileName, byte[] content) throws Exception {

        String response = mockMvc.perform(multipart("/finance/api/v1/imports")
                .file(new MockMultipartFile("file", fileName, "text/csv", content))
                .param("accountId", String.valueOf(accountId)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private JsonNode preview(long batchId) throws Exception {

        return objectMapper.readTree(body("/finance/api/v1/imports/" + batchId));
    }

    private String body(String url) throws Exception {

        return mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private long createAccount(String name, String currency) throws Exception {

        String body = mockMvc.perform(post("/finance/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"BANK","currency":"%s","openingBalanceMinor":0,
                     "openingBalanceOn":"2026-01-01"}""".formatted(name, currency)))
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

    private byte[] statement(String... rows) {

        String header = """
            Bank Pekao S.A.;;;;;
            ;;;;;
            #Za okres:;2026-02-01;2026-02-28;;;
            #Saldo początkowe;0,00;;;;
            ;;;;;
            #Data księgowania;#Tytułem;#Nadawca / Odbiorca;#Numer referencyjny;#Kwota operacji;#Kwota w walucie operacji
            """;
        String body = String.join("\n", rows);
        return (header + body + "\n").getBytes(WINDOWS_1250);
    }

    private String row(String date, String amount, String description, String reference) {

        return "%s;\"%s\";\"Sklep\";\"%s\";%s;".formatted(date, description, reference, amount);
    }
}
