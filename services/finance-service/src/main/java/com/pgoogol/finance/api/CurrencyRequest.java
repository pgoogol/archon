package com.pgoogol.finance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CurrencyRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "kod waluty to trzy litery")
    String code,

    @NotBlank @Size(max = 50)
    String name,

    @NotNull @Min(0) @Max(4)
    Integer minorUnit) {

}
