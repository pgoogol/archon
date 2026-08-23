package com.pgoogol.finance.api;

import com.pgoogol.finance.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AccountRequest(
    @NotBlank @Size(max = 100)
    String name,

    @NotNull
    AccountType type,

    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "kod waluty to trzy litery")
    String currency,

    @Size(max = 34)
    String iban,

    /** Saldo otwarcia bywa ujemne (debet, karta kredytowa), więc bez ograniczenia znaku. */
    @NotNull
    Long openingBalanceMinor,

    @NotNull
    LocalDate openingBalanceOn) {

}
