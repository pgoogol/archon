package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.ConnectionEvent;
import com.pgoogol.diagnostics.core.report.Finding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pula z jednym połączeniem i dwa równoległe żądania, z których każde trzyma połączenie
 * przez 250 ms: drugie musi czekać na pulę, a oba trzymają połączenie bezczynnie.
 */
@SpringBootTest(classes = DiagnosticsTestApplication.class, properties = {
    "diagnostics.mode=dev",
    "diagnostics.output.jsonl.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ConnectionPoolPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingFindingReporter reporter;

    @BeforeEach
    void clearReports() {

        reporter.clear();
    }

    @Test
    @DisplayName("drugie żądanie czeka na jedyne połączenie, a oba trzymają je bezczynnie")
    void requests_whenPoolHasOneConnection_reportWaitAndHold() throws Exception {

        // given
        Callable<Integer> request = () -> mockMvc.perform(get("/transactions/hold")).andReturn().getResponse().getStatus();

        // when
        List<Integer> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            statuses = executor.invokeAll(List.of(request, request)).stream()
                .map(future -> future.resultNow())
                .toList();
        }

        // then
        List<Finding> waits = reporter.findings("CONNECTION_WAIT");
        List<Finding> holds = reporter.findings("CONNECTION_HOLD");
        Duration longestWait = reporter.activities(ConnectionEvent.class).stream()
            .map(ConnectionEvent::waitTime)
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);
        assertAll(
            () -> assertThat(statuses).containsExactly(200, 200),
            () -> assertThat(waits).hasSize(1),
            () -> assertThat(longestWait).isGreaterThanOrEqualTo(Duration.ofMillis(200)),
            () -> assertThat(holds).hasSize(2),
            () -> assertThat(holds).allSatisfy(hold -> assertThat(hold.measured()).isGreaterThanOrEqualTo(250)));
    }
}
