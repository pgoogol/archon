package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.TransactionOutcome;
import com.pgoogol.diagnostics.core.report.Finding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Transakcje Springa na prawdziwym PostgreSQL: każda rozpoczęta transakcja daje jedno zdarzenie. */
@SpringBootTest(classes = DiagnosticsTestApplication.class, properties = {
    "diagnostics.mode=dev",
    "diagnostics.output.jsonl.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Sql("/test-data/orders.sql")
class TransactionsPostgresIntegrationTest {

    private static final String SCENARIOS = TransactionScenarios.class.getName();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingFindingReporter reporter;

    @BeforeEach
    void clearReports() {

        reporter.clear();
    }

    @Test
    @DisplayName("@Transactional z samymi odczytami daje zatwierdzoną transakcję i wskazówkę readOnly")
    void request_whenDeclarativeReadTransaction_reportsReadOnlyCandidate() throws Exception {

        // when
        run("declarative");

        // then
        TransactionEvent transaction = transactions().getFirst();
        List<Finding> candidates = reporter.findings("READ_ONLY_CANDIDATE");
        assertAll(
            () -> assertThat(transactions()).hasSize(1),
            () -> assertThat(transaction.name()).isEqualTo(SCENARIOS + ".declarativeRead"),
            () -> assertThat(transaction.outcome()).isEqualTo(TransactionOutcome.COMMITTED),
            () -> assertThat(transaction.readOnly()).isFalse(),
            () -> assertThat(transaction.reads()).isEqualTo(1),
            () -> assertThat(transaction.writes()).isZero(),
            () -> assertThat(candidates).extracting(Finding::subject).containsExactly(SCENARIOS + ".declarativeRead"));
    }

    @Test
    @DisplayName("TransactionTemplate daje transakcję bez nazwy z policzonym zapisem")
    void request_whenTransactionTemplate_reportsUnnamedTransactionWithWrite() throws Exception {

        // when
        run("programmatic");

        // then
        TransactionEvent transaction = transactions().getFirst();
        assertAll(
            () -> assertThat(transaction.name()).isEqualTo("(bez nazwy)"),
            () -> assertThat(transaction.outcome()).isEqualTo(TransactionOutcome.COMMITTED),
            () -> assertThat(transaction.writes()).isEqualTo(1));
    }

    @Test
    @DisplayName("wyjątek z metody transakcyjnej daje transakcję wycofaną")
    void request_whenMethodThrows_reportsRolledBackTransaction() throws Exception {

        // when
        run("rollback");

        // then
        assertThat(transactions())
            .extracting(TransactionEvent::outcome)
            .containsExactly(TransactionOutcome.ROLLED_BACK);
    }

    @Test
    @DisplayName("REQUIRES_NEW daje osobną transakcję wewnętrzną, a jej zapis nie liczy się do zewnętrznej")
    void request_whenRequiresNewInside_reportsInnerAndOuterSeparately() throws Exception {

        // when
        run("requires-new");

        // then
        TransactionEvent inner = transactions().get(0);
        TransactionEvent outer = transactions().get(1);
        assertAll(
            () -> assertThat(transactions()).hasSize(2),
            () -> assertThat(inner.name()).isEqualTo(AuditWriter.class.getName() + ".recordInNewTransaction"),
            () -> assertThat(inner.depth()).isEqualTo(1),
            () -> assertThat(inner.writes()).isEqualTo(1),
            () -> assertThat(outer.name()).isEqualTo(SCENARIOS + ".outerWithRequiresNew"),
            () -> assertThat(outer.depth()).isZero(),
            () -> assertThat(outer.reads()).isEqualTo(1),
            () -> assertThat(outer.writes()).isZero());
    }

    private void run(String way) throws Exception {

        mockMvc.perform(get("/transactions/{way}", way))
            .andExpect(status().isOk());
    }

    private List<TransactionEvent> transactions() {

        return reporter.activities(TransactionEvent.class);
    }
}
