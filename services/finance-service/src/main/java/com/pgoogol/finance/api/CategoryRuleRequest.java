package com.pgoogol.finance.api;

import com.pgoogol.finance.categorization.domain.MatchField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param pattern  fragment tekstu; dopasowanie jest zawieraniem, nie wyrażeniem
 *                 regularnym
 * @param priority niższa liczba wygrywa; przy równej rozstrzyga identyfikator
 */
public record CategoryRuleRequest(
    @NotBlank @Size(max = 255)
    String pattern,

    @NotNull
    MatchField matchField,

    @NotNull
    Long categoryId,

    Integer priority,

    Boolean active) {

}
