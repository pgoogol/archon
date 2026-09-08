package com.pgoogol.kitchen;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Realny Postgres 16 do testów (nigdy H2 — schemat stoi na rozszerzeniach
 * {@code pg_trgm} i {@code unaccent}, których H2 nie ma); jeden kontener
 * współdzielony w ramach cache'owanego kontekstu Springa.
 *
 * <p>Kontener startuje domyślnie. Gdy środowisko ma już Postgresa pod ręką —
 * albo nie może pobrać obrazu — ustaw {@code TEST_POSTGRES_CONTAINER=false}
 * i podaj {@code SPRING_DATASOURCE_URL/USERNAME/PASSWORD}. Tamta baza musi być
 * dedykowana i pusta.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "test.postgres.container", havingValue = "true",
        matchIfMissing = true)
    PostgreSQLContainer<?> postgresContainer() {

        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
