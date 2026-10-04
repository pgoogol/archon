package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.jdbc.CapturedDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** {@code diagnostics.enabled=false} w prawdziwej aplikacji: żadnego silnika i żadnego proxy. */
@SpringBootTest(classes = DiagnosticsTestApplication.class, properties = {
    "diagnostics.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false"})
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class DataDiagnosticsDisabledIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("wyłączony starter nie owija DataSource i nie tworzy silnika")
    void startup_whenDisabled_leavesDataSourceAlone() {

        // then
        assertAll(
            () -> assertThat(dataSource).isNotInstanceOf(CapturedDataSource.class),
            () -> assertThat(context.getBeansOfType(DiagnosticsEngine.class)).isEmpty());
    }
}
