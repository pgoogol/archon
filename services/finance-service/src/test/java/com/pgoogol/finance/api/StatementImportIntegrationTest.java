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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Definicja ukończenia etapu 2: ten sam plik zaimportowany dwa razy nie tworzy
 * duplikatów, nachodzące zakresy dat też nie — a dwie identyczne operacje tego
 * samego dnia zostają dwiema operacjami.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class StatementImportIntegrationTest {

    private static final Charset WINDOWS_1250 = Charset.forName("windows-1250");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clearFinanceTables() throws Exception {

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                truncate table finance.transaction, finance.import_row, finance.import_batch, \
                finance.account, finance.category restart identity cascade""");
            statement.execute("delete from finance.exchange_rate");
        }
    }

    @Test
    @DisplayName("import gdy ten sam plik wgrywany jest drugi raz, odrzuca go po skrócie")
    void upload_whenSameFileIsUploadedTwice_rejectsItByHash() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        byte[] wyciag = statement(row("2026-01-05", "-45,00", "ZAKUP", ""));
        upload(account, "styczen.csv", wyciag);

        // when & then: nazwa pliku nie ma znaczenia — liczy się zawartość
        mockMvc.perform(multipart("/finance/api/v1/imports")
                .file(new MockMultipartFile("file", "inna-nazwa.csv", "text/csv", wyciag))
                .param("accountId", String.valueOf(account)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("STATEMENT_ALREADY_IMPORTED"));
    }

    @Test
    @DisplayName("import gdy plik ma dwie identyczne operacje, tworzy dwie transakcje")
    void commit_whenFileHasTwoIdenticalOperations_createsTwoTransactions() throws Exception {

        // given: dwie takie same kawy tego samego dnia zdarzają się naprawdę
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Jedzenie", "EXPENSE");
        byte[] wyciag = statement(
            row("2026-01-05", "-12,00", "KAWA", ""),
            row("2026-01-05", "-12,00", "KAWA", ""));

        // when
        long batch = upload(account, "styczen.csv", wyciag);
        JsonNode committed = commit(batch, category);

        // then
        assertThat(committed.get("committedCount").asInt()).isEqualTo(2);
        assertThat(transactionCount(account)).isEqualTo(2);
    }

    @Test
    @DisplayName("import gdy drugi plik ma nachodzący zakres dat, nie tworzy duplikatów")
    void commit_whenSecondFileOverlapsDateRange_createsNoDuplicates() throws Exception {

        // given: styczeń zaimportowany, potem plik za cały kwartał z tym samym
        // styczniem w środku
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Jedzenie", "EXPENSE");
        byte[] styczen = statement(
            row("2026-01-05", "-45,00", "ZAKUP", ""),
            row("2026-01-12", "-30,00", "OBIAD", ""));
        long pierwsza = upload(account, "styczen.csv", styczen);
        commit(pierwsza, category);

        byte[] kwartal = statement(
            row("2026-01-05", "-45,00", "ZAKUP", ""),
            row("2026-01-12", "-30,00", "OBIAD", ""),
            row("2026-02-03", "-60,00", "KOLACJA", ""));

        // when
        long druga = upload(account, "kwartal.csv", kwartal);
        JsonNode podglad = preview(druga);
        JsonNode wynik = commit(druga, category);

        // then: dwa wiersze rozpoznane jako duplikaty, wchodzi wyłącznie luty
        assertThat(podglad.get("batch").get("duplicateCount").asInt()).isEqualTo(2);
        assertThat(podglad.get("batch").get("newCount").asInt()).isEqualTo(1);
        assertThat(wynik.get("committedCount").asInt()).isEqualTo(1);
        assertThat(wynik.get("skippedDuplicateCount").asInt()).isEqualTo(2);
        assertThat(transactionCount(account)).isEqualTo(3);
    }

    @Test
    @DisplayName("import gdy wiersze mają referencje bankowe, deduplikuje po nich")
    void commit_whenRowsHaveBankReferences_deduplicatesByThem() throws Exception {

        // given: przy referencji bankowej zmiana opisu nie robi z operacji nowej
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Jedzenie", "EXPENSE");
        long pierwsza = upload(account, "a.csv", statement(row("2026-01-05", "-45,00", "ZAKUP", "REF-1")));
        commit(pierwsza, category);

        // when: ten sam identyfikator operacji, inny opis
        long druga = upload(account, "b.csv",
            statement(row("2026-01-05", "-45,00", "ZAKUP KARTĄ W SKLEPIE", "REF-1")));
        JsonNode wynik = commit(druga, category);

        // then
        assertThat(wynik.get("committedCount").asInt()).isZero();
        assertThat(transactionCount(account)).isEqualTo(1);
    }

    @Test
    @DisplayName("podgląd przed zatwierdzeniem nie tworzy żadnej transakcji")
    void upload_beforeCommit_createsNoTransactions() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        createCategory("Jedzenie", "EXPENSE");

        // when
        long batch = upload(account, "styczen.csv", statement(row("2026-01-05", "-45,00", "ZAKUP", "")));

        // then: to jest cały sens rozdzielenia kroków — użytkownik widzi, co się
        // stanie, zanim się stanie
        assertThat(preview(batch).get("rows")).hasSize(1);
        assertThat(transactionCount(account)).isZero();
    }

    @Test
    @DisplayName("zatwierdzenie gdy jeden wiersz nie ma kategorii, nie zapisuje żadnego")
    void commit_whenOneRowHasNoCategory_savesNone() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Jedzenie", "EXPENSE");
        long batch = upload(account, "styczen.csv", statement(
            row("2026-01-05", "-45,00", "ZAKUP", ""),
            row("2026-01-06", "-60,00", "OBIAD", "")));
        List<Long> rowIds = rowIdsOf(batch);

        // when: kategoria tylko dla pierwszego wiersza
        String body = """
            {"categoryAssignments":[{"rowId":%d,"categoryId":%d}]}"""
            .formatted(rowIds.getFirst(), category);
        mockMvc.perform(post("/finance/api/v1/imports/{id}/commit", batch)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());

        // then: albo wszystkie, albo żadna — wyciąg zaimportowany w połowie byłby
        // gorszy niż niezaimportowany wcale
        assertThat(transactionCount(account)).isZero();
    }

    @Test
    @DisplayName("zatwierdzenie uzgadnia saldo końcowe wyciągu z wyliczonym")
    void commit_reconcilesStatementClosingBalanceWithComputed() throws Exception {

        // given: saldo otwarcia konta to 0, wyciąg mówi, że po operacji ma być -45,00
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Jedzenie", "EXPENSE");
        long batch = upload(account, "styczen.csv", statement(row("2026-01-05", "-45,00", "ZAKUP", "")));

        // when
        JsonNode wynik = commit(batch, category);

        // then
        JsonNode uzgodnienie = wynik.get("reconciliation");
        assertThat(uzgodnienie.get("matched").asBoolean()).isTrue();
        assertThat(uzgodnienie.get("computedBalanceMinor").asLong()).isEqualTo(-4500L);
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

        String response = mockMvc.perform(get("/finance/api/v1/imports/{id}", batchId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode commit(long batchId, long categoryId) throws Exception {

        List<Long> rowIds = newRowIdsOf(batchId);
        String assignments = rowIds.stream()
            .map(rowId -> """
                {"rowId":%d,"categoryId":%d}""".formatted(rowId, categoryId))
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        String body = """
            {"categoryAssignments":[%s]}""".formatted(assignments);
        String response = mockMvc.perform(post("/finance/api/v1/imports/{id}/commit", batchId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private List<Long> rowIdsOf(long batchId) throws Exception {

        return collectRowIds(batchId, false);
    }

    private List<Long> newRowIdsOf(long batchId) throws Exception {

        return collectRowIds(batchId, true);
    }

    private List<Long> collectRowIds(long batchId, boolean onlyNew) throws Exception {

        JsonNode rows = preview(batchId).get("rows");
        List<Long> ids = new ArrayList<>();
        rows.forEach(row -> appendId(ids, row, onlyNew));
        return List.copyOf(ids);
    }

    private void appendId(List<Long> ids, JsonNode row, boolean onlyNew) {

        boolean isNew = "NEW".equals(row.get("status").asString());
        if (!onlyNew || isNew) {

            ids.add(row.get("id").asLong());
        }
    }

    private int transactionCount(long accountId) throws Exception {

        String response = mockMvc.perform(get("/finance/api/v1/transactions")
                .param("accountId", String.valueOf(accountId)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("totalElements").asInt();
    }

    private long createAccount(String name, String currency) throws Exception {

        String body = """
            {"name":"%s","type":"BANK","currency":"%s","openingBalanceMinor":0,
             "openingBalanceOn":"2026-01-01"}""".formatted(name, currency);
        String response = mockMvc.perform(post("/finance/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createCategory(String name, String direction) throws Exception {

        String body = """
            {"name":"%s","direction":"%s"}""".formatted(name, direction);
        String response = mockMvc.perform(post("/finance/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    /** Wyciąg w formacie Pekao, w kodowaniu windows-1250 — tak jak z banku. */
    private byte[] statement(String... rows) {

        String header = """
            Bank Pekao S.A.;;;;;
            ;;;;;
            #Za okres:;2026-01-01;2026-03-31;;;
            #Saldo początkowe;0,00;;;;
            ;;;;;
            #Data księgowania;#Tytułem;#Nadawca / Odbiorca;#Numer referencyjny;#Kwota operacji;#Kwota w walucie operacji
            """;
        String body = String.join("\n", rows);
        String footer = """

            ;;;;;
            #Saldo końcowe;-45,00;;;;
            """;
        return (header + body + footer).getBytes(WINDOWS_1250);
    }

    private String row(String date, String amount, String description, String reference) {

        return "%s;\"%s\";\"Sklep\";\"%s\";%s;".formatted(date, description, reference, amount);
    }
}
