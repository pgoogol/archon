package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
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

/**
 * Wnętrze Hibernate na prawdziwym PostgreSQL, z cache drugiego poziomu. Progi są obniżone,
 * żeby małe dane testowe je przekraczały; czasu sprawdzania zmian nie da się wymusić
 * dokładnie, więc próg to 1 ms przy trzech tysiącach zarządzanych encji.
 */
@SpringBootTest(classes = DiagnosticsTestApplication.class, properties = {
    "diagnostics.mode=dev",
    "diagnostics.output.jsonl.enabled=false",
    "diagnostics.analyzers.entity-load.threshold=10",
    "diagnostics.analyzers.dirty-checking.threshold=1",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
    "spring.jpa.properties.hibernate.cache.region.factory_class=jcache",
    "spring.jpa.properties.hibernate.javax.cache.provider=com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider",
    "spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=create"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Sql("/test-data/orders.sql")
class HibernatePostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingFindingReporter reporter;

    @BeforeEach
    void clearReports() {

        reporter.clear();
    }

    @Test
    @DisplayName("flush w pętli daje wniosek o liczbie flushy w jednostce")
    void request_whenFlushInLoop_reportsFlushCount() throws Exception {

        // when
        run("flush-loop");

        // then
        List<Finding> findings = reporter.findings("FLUSH_COUNT");
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(findings.getFirst().measured()).isGreaterThanOrEqualTo(12));
    }

    @Test
    @DisplayName("załadowanie wszystkich encji daje wniosek o liczbie encji z ich dokładną liczbą")
    void request_whenEverythingLoaded_reportsEntityLoad() throws Exception {

        // when
        run("load-all");

        // then: sześć zamówień i dwanaście pozycji
        List<Finding> findings = reporter.findings("ENTITY_LOAD");
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(18));
    }

    @Test
    @DisplayName("flush przy tysiącach zarządzanych encji daje wniosek o sprawdzaniu zmian")
    void request_whenThousandsOfManagedEntitiesFlushed_reportsDirtyChecking() throws Exception {

        // given
        run("seed");
        reporter.clear();

        // when
        run("load-all");

        // then
        long dirtyChecks = reporter.activities(PersistenceSessionEvent.class).stream()
            .mapToLong(PersistenceSessionEvent::dirtyChecks)
            .sum();
        assertAll(
            () -> assertThat(dirtyChecks).isGreaterThanOrEqualTo(3_000),
            () -> assertThat(reporter.findings("DIRTY_CHECKING")).hasSize(1));
    }

    @Test
    @DisplayName("cache drugiego poziomu, który tylko chybia, daje wskazówkę o współczynniku trafień")
    void request_whenCacheOnlyMisses_reportsSecondLevelCache() throws Exception {

        // when
        run("cache-misses");

        // then
        List<Finding> findings = reporter.findings("SECOND_LEVEL_CACHE");
        long puts = reporter.activities(PersistenceSessionEvent.class).stream()
            .mapToLong(PersistenceSessionEvent::cachePuts)
            .sum();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(findings.getFirst().measured()).isZero(),
            () -> assertThat(puts).isEqualTo(25));
    }

    private void run(String way) throws Exception {

        mockMvc.perform(get("/hibernate/{way}", way))
            .andExpect(status().isOk());
    }
}
