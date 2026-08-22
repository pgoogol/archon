package com.pgoogol.finance.report;

import java.util.List;

/** @param monthsMinor dwanaście kwot, od stycznia do grudnia */
public record YearlyMatrixRow(
    long categoryId,
    String categoryName,
    List<Long> monthsMinor,
    long totalMinor) {

}
