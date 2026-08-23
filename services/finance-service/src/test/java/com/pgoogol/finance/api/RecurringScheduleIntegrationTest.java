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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Definicja ukończenia etapu 3: generator jest idempotentny i obsłużony jest
 * 31 dzień miesiąca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class RecurringScheduleIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

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
                truncate table finance.scheduled_occurrence, finance.recurring_rule, \
                finance.transaction, finance.import_row, finance.import_batch, \
                finance.account, finance.category restart identity cascade""");
            statement.execute("delete from finance.exchange_rate");
        }
    }

    @Test
    @DisplayName("generowanie uruchomione dwa razy nie zmienia liczby pozycji")
    void generate_whenRunTwice_leavesOccurrenceCountUnchanged() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");
        createRule(account, category, "MONTHLY", 10, TODAY.withDayOfMonth(1), null);
        int afterSave = occurrenceCount();

        // when: pierwszy ręczny przebieg po tym, który zrobił się przy zapisie
        JsonNode firstRun = generate();
        int afterFirstRun = occurrenceCount();
        JsonNode secondRun = generate();

        // then
        assertThat(firstRun.get("createdCount").asInt()).isZero();
        assertThat(secondRun.get("createdCount").asInt()).isZero();
        assertThat(afterFirstRun).isEqualTo(afterSave);
        assertThat(occurrenceCount()).isEqualTo(afterSave);
    }

    @Test
    @DisplayName("reguła z 31 dniem miesiąca wypada ostatniego dnia krótkiego miesiąca")
    void createRule_whenDayIs31_fallsOnLastDayOfShortMonth() throws Exception {

        // given: okno generatora sięga 12 miesięcy w przód, więc luty zawsze
        // gdzieś w nim jest
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");

        // when
        createRule(account, category, "MONTHLY", 31, TODAY.withDayOfMonth(1), null);

        // then: żaden termin nie wypada 1 dnia miesiąca — to byłby ślad po
        // naiwnym plusMonths, który przelewa 31 na kolejny miesiąc
        JsonNode schedule = occurrences();
        assertThat(schedule).isNotEmpty();
        schedule.forEach(occurrenceId -> {

            LocalDate dueDate = LocalDate.parse(occurrenceId.get("dueDate").asText());
            assertThat(dueDate.getDayOfMonth()).isEqualTo(dueDate.lengthOfMonth());
        });
    }

    @Test
    @DisplayName("reguła z datą końca przestaje generować po niej")
    void createRule_whenEndsOnIsSet_stopsGeneratingAfterIt() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");
        LocalDate start = TODAY.withDayOfMonth(1);
        LocalDate endsOn = start.plusMonths(3);

        // when
        createRule(account, category, "MONTHLY", 1, start, endsOn);

        // then: cztery terminy — start i trzy kolejne miesiące
        JsonNode schedule = occurrences();
        assertThat(schedule).hasSize(4);
        LocalDate lastDueDate = LocalDate.parse(schedule.get(3).get("dueDate").asText());
        assertThat(lastDueDate).isEqualTo(endsOn);
    }

    @Test
    @DisplayName("pozycja czekająca po terminie ma status OVERDUE, choć w bazie jej nie ma")
    void listOccurrences_whenPendingIsPastDue_reportsOverdue() throws Exception {

        // given: reguła założona pół roku wstecz ma zaległości od pierwszego dnia
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");
        createRule(account, category, "MONTHLY", 1, TODAY.minusMonths(6).withDayOfMonth(1), null);

        // when
        JsonNode overdue = occurrences("status=OVERDUE");

        // then
        assertThat(overdue).isNotEmpty();
        overdue.forEach(occurrenceId -> {

            LocalDate dueDate = LocalDate.parse(occurrenceId.get("dueDate").asText());
            assertThat(dueDate).isBefore(TODAY);
            assertThat(occurrenceId.get("status").asText()).isEqualTo("OVERDUE");
        });
        assertThat(storedStatuses()).doesNotContain("OVERDUE");
    }

    @Test
    @DisplayName("zapłacenie pozycji tworzy transakcję na faktyczną kwotę")
    void payOccurrence_createsTransactionForActualAmount() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");
        createRule(account, category, "MONTHLY", 1, TODAY.minusMonths(1).withDayOfMonth(1), null);
        long occurrenceId = occurrences().get(0).get("id").asLong();

        // when: rachunek przyszedł na więcej, niż zakładała reguła
        String body = """
            {"paidOn":"%s","paidAmountMinor":13750}""".formatted(TODAY);
        String responseBody = mockMvc.perform(post("/finance/api/v1/occurrences/{id}/pay", occurrenceId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.paidAmountMinor").value(13750))
            .andReturn().getResponse().getContentAsString();

        // then
        JsonNode paidId = objectMapper.readTree(responseBody);
        long transactionId = paidId.get("transactionId").asLong();
        mockMvc.perform(get("/finance/api/v1/transactions/{id}", transactionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amountMinor").value(13750))
            .andExpect(jsonPath("$.type").value("EXPENSE"))
            .andExpect(jsonPath("$.categoryId").value(Math.toIntExact(category)));
        assertThat(paidId.get("expectedAmountMinor").asLong()).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("zapłacenie pozycji drugi raz kończy się konfliktem")
    void payOccurrence_whenAlreadyPaid_reportsConflict() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");
        createRule(account, category, "MONTHLY", 1, TODAY.minusMonths(1).withDayOfMonth(1), null);
        long occurrenceId = occurrences().get(0).get("id").asLong();
        String body = """
            {"paidOn":"%s","paidAmountMinor":12000}""".formatted(TODAY);
        mockMvc.perform(post("/finance/api/v1/occurrences/{id}/pay", occurrenceId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        // when & then: druga płatność założyłaby drugą transakcję na ten sam rachunek
        mockMvc.perform(post("/finance/api/v1/occurrences/{id}/pay", occurrenceId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("OCCURRENCE_ALREADY_SETTLED"));
    }

    @Test
    @DisplayName("wyłączenie reguły usuwa przyszłe pozycje, a rozliczone zostawia")
    void deactivateRule_dropsFuturePendingAndKeepsSettled() throws Exception {

        // given: jedna pozycja zaległa zapłacona, jedna pominięta, reszta czeka
        long account = createAccount("Bieżące", "PLN");
        long category = createCategory("Mieszkanie", "EXPENSE");
        long rule = createRule(account, category, "MONTHLY", 1,
            TODAY.minusMonths(2).withDayOfMonth(1), null);
        JsonNode overdue = occurrences("status=OVERDUE");
        long paidId = overdue.get(0).get("id").asLong();
        long skippedId = overdue.get(1).get("id").asLong();
        mockMvc.perform(post("/finance/api/v1/occurrences/{id}/pay", paidId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"paidOn":"%s","paidAmountMinor":12000}""".formatted(TODAY)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/finance/api/v1/occurrences/{id}/skip", skippedId))
            .andExpect(status().isOk());

        // when
        mockMvc.perform(delete("/finance/api/v1/recurring-rules/{id}", rule))
            .andExpect(status().isNoContent());

        // then: historia zostaje, przyszłość znika
        JsonNode remaining = occurrences();
        assertThat(remaining).isNotEmpty();
        remaining.forEach(occurrenceId -> {

            String status = occurrenceId.get("status").asText();
            LocalDate dueDate = LocalDate.parse(occurrenceId.get("dueDate").asText());
            boolean settled = status.equals("PAID") || status.equals("SKIPPED");
            assertThat(settled || dueDate.isBefore(TODAY)).isTrue();
        });
        mockMvc.perform(get("/finance/api/v1/recurring-rules/{id}", rule))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("reguła na kategorię o przeciwnym kierunku kończy się błędem")
    void createRule_whenCategoryDirectionMismatches_reportsBadRequest() throws Exception {

        // given
        long account = createAccount("Bieżące", "PLN");
        long incomeCategory = createCategory("Wypłata", "INCOME");

        // when & then
        String body = ruleBody(account, incomeCategory, "MONTHLY", 1, TODAY.withDayOfMonth(1), null);
        mockMvc.perform(post("/finance/api/v1/recurring-rules")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("CATEGORY_DIRECTION_MISMATCH"));
    }

    private long createRule(long accountId, long categoryId, String frequency, int dayOfMonth,
                            LocalDate startsOn, LocalDate endsOn) throws Exception {

        String body = ruleBody(accountId, categoryId, frequency, dayOfMonth, startsOn, endsOn);
        String response = mockMvc.perform(post("/finance/api/v1/recurring-rules")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String ruleBody(long accountId, long categoryId, String frequency, int dayOfMonth,
                            LocalDate startsOn, LocalDate endsOn) {

        String endsOnJson = "null";
        if (Objects.nonNull(endsOn)) {

            endsOnJson = "\"%s\"".formatted(endsOn);
        }
        return """
            {"name":"Prąd","accountId":%d,"categoryId":%d,"type":"EXPENSE",
             "amountMinor":12000,"frequency":"%s","dayOfMonth":%d,
             "startsOn":"%s","endsOn":%s}"""
            .formatted(accountId, categoryId, frequency, dayOfMonth, startsOn, endsOnJson);
    }

    private JsonNode generate() throws Exception {

        String response = mockMvc.perform(post("/finance/api/v1/recurring-rules/generate"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode occurrences() throws Exception {

        return occurrences(null);
    }

    private JsonNode occurrences(String query) throws Exception {

        String url = "/finance/api/v1/occurrences";
        if (Objects.nonNull(query)) {

            url = url + "?" + query;
        }
        String response = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private int occurrenceCount() throws Exception {

        return occurrences().size();
    }

    private List<String> storedStatuses() throws Exception {

        List<String> statuses = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows =
                 statement.executeQuery("select distinct status from finance.scheduled_occurrence")) {

            while (rows.next()) {

                statuses.add(rows.getString(1));
            }
        }
        return statuses;
    }

    private long createAccount(String name, String currency) throws Exception {

        String body = mockMvc.perform(post("/finance/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"BANK","currency":"%s","openingBalanceMinor":0,
                     "openingBalanceOn":"%s"}""".formatted(name, currency, TODAY.minusYears(1))))
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
