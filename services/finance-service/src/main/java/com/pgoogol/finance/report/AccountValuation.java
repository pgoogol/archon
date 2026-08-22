package com.pgoogol.finance.report;

import org.springframework.lang.Nullable;

/**
 * Saldo konta wraz z wyceną bieżącą.
 *
 * @param baseValueMinor {@code null}, gdy waluta konta nie ma jeszcze kursu —
 *                       pulpit ma wtedy pokazać saldo, a nie pustą stronę
 */
public record AccountValuation(
    long accountId,
    String accountName,
    String currency,
    int minorUnit,
    long balanceMinor,
    @Nullable Long baseValueMinor) {

}
