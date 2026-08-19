package com.pgoogol.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Dym: kontekst wstaje, migracje przechodzą, a Hibernate potwierdza, że schemat
 * z Flyway zgadza się z encjami ({@code ddl-auto: validate}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class FinanceServiceApplicationTests {

    @Test
    @DisplayName("kontekst aplikacji wstaje na świeżej bazie")
    void contextLoads() {

    }
}
