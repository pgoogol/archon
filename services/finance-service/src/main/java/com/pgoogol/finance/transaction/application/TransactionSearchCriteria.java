package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.transaction.domain.TransactionType;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/** Filtry listy transakcji; każde pole opcjonalne, null znaczy "bez ograniczenia". */
public record TransactionSearchCriteria(
    @Nullable LocalDate from,
    @Nullable LocalDate to,
    @Nullable Long accountId,
    @Nullable Long categoryId,
    @Nullable TransactionType type,
    @Nullable String currency) {

}
