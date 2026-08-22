package com.pgoogol.finance.api;

import com.pgoogol.finance.categorization.domain.MatchField;

public record CategoryRuleResponse(
    long id,
    String pattern,
    MatchField matchField,
    long categoryId,
    String categoryName,
    int priority,
    boolean active) {

}
