package com.pgoogol.finance.api;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Liczby dziesiętne wychodzą na API jako tekst, nie jako liczba JSON-owa.
 * Klient w JavaScripcie zamieniłby liczbę na {@code double}, a w tym module
 * żadna wartość pieniężna nie ma prawa przez niego przejść.
 *
 * <p>{@code toPlainString} zamiast {@code toString}, bo drugie potrafi zwrócić
 * zapis wykładniczy.</p>
 */
@Component
public class DecimalMapper {

    @Nullable
    public String toPlainString(@Nullable BigDecimal value) {
        return Objects.isNull(value) ? null : value.toPlainString();
    }
}
