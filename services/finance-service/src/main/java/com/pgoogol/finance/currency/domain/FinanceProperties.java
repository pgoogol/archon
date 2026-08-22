package com.pgoogol.finance.currency.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Waluta bazowa modułu — w niej liczą się wszystkie zestawienia zbiorcze.
 *
 * <p>Zmiana po wprowadzeniu danych wymaga przeliczenia {@code base_amount_minor}
 * każdej istniejącej transakcji, więc jest to decyzja jednorazowa, a nie
 * przełącznik prezentacji.</p>
 */
@ConfigurationProperties(prefix = "finance")
public record FinanceProperties(String baseCurrency) {

    public FinanceProperties {

        Objects.requireNonNull(baseCurrency, "baseCurrency");
    }

    public boolean isBase(String code) {

        return Objects.equals(baseCurrency, code);
    }
}
