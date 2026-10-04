package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.jdbc.CapturedDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/**
 * Starter w prawdziwej aplikacji na PostgreSQL: owinięty {@code DataSource}, granica HTTP,
 * analiza N+1 i miejsce wywołania, dla każdego sposobu, w jaki aplikacja dociąga dane.
 */
@SpringBootTest(classes = DiagnosticsTestApplication.class, properties = {
    "diagnostics.mode=dev",
    "diagnostics.output.jsonl.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Sql("/test-data/orders.sql")
class DataDiagnosticsPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingFindingReporter reporter;

    @Autowired
    private HikariDataSource hikariDataSource;

    @BeforeEach
    void clearReports() {

        reporter.clear();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "repository,    byRepository,     OrderItemRepository.findByOrderId",
        "jpql,          byJpql,",
        "criteria,      itemsByCriteria,",
        "native,        byNativeQuery,",
        "jdbc-template, byJdbcTemplate,",
        "lazy,          byLazyLoading,"})
    @DisplayName("pozycje dociągane w pętli dają wniosek N+1 wskazujący metodę serwisu")
    void request_whenItemsLoadedPerOrder_reportsNPlusOneAtServiceMethod(String way, String method,
                                                                         String repositoryMethod) throws Exception {

        // when
        mockMvc.perform(get("/orders/items/{way}", way))
            .andExpect(status().isOk());

        // then
        List<Finding> findings = reporter.findings("N_PLUS_ONE");
        Finding finding = findings.getFirst();
        CallSite caller = finding.shapes().getFirst().callers().getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.measured()).isEqualTo(6),
            () -> assertThat(finding.shapes().getFirst().shape()).contains("order_item"),
            () -> assertThat(caller.className()).isEqualTo(OrderService.class.getName()),
            () -> assertThat(caller.method()).isEqualTo(method),
            () -> assertThat(caller.line()).isPositive(),
            () -> assertThat(caller.repositoryMethod()).isEqualTo(repositoryMethod));
    }

    @Test
    @DisplayName("jednostka żądania nosi wzorzec trasy i liczy wszystkie zapytania")
    void request_whenServed_reportsUnitNamedAfterRoute() throws Exception {

        // when
        mockMvc.perform(get("/orders/items/{way}", "repository"))
            .andExpect(status().isOk());

        // then
        CapturingFindingReporter.Report report = reporter.reports().getFirst();
        assertAll(
            () -> assertThat(report.unit().name()).isEqualTo("GET /orders/items/{way}"),
            () -> assertThat(report.unit().operationCount()).isEqualTo(7));
    }

    @Test
    @DisplayName("owinięty HikariDataSource dalej wstrzykuje się po swoim typie")
    void dataSource_whenInjectedByType_isCapturingHikariDataSource() {

        // then
        assertThat(hikariDataSource).isInstanceOf(CapturedDataSource.class);
    }

    @Test
    @DisplayName("zapytania Flyway przy starcie nie dają raportu, bo leżą poza jednostką pracy")
    void startup_whenFlywayMigrated_reportsNothing() {

        // then
        assertThat(reporter.outsideUnits()).isEmpty();
    }
}
