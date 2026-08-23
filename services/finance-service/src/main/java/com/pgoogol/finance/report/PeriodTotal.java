package com.pgoogol.finance.report;

import java.time.LocalDate;

/** Suma okresu, policzona z surowych przepływów — bez podwójnego liczenia gałęzi. */
public record PeriodTotal(LocalDate period, long amountMinor) {

}
