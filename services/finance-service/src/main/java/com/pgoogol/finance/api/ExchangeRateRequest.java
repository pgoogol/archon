package com.pgoogol.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Kurs jako tekst dziesiętny, nie liczba zmiennoprzecinkowa — w tym module
 * żadna wartość pieniężna nie przechodzi przez {@code double}.
 */
public record ExchangeRateRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "kod waluty to trzy litery")
    String code,

    @NotNull
    LocalDate rateDate,

    @NotBlank
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,8})?$",
        message = "kurs to liczba dodatnia, najwyżej 8 miejsc po przecinku")
    String rate) {

}
