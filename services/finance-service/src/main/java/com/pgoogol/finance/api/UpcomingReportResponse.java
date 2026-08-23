package com.pgoogol.finance.api;

import com.pgoogol.finance.report.UpcomingItem;

import java.time.LocalDate;
import java.util.List;

public record UpcomingReportResponse(
    LocalDate horizonTo,
    List<UpcomingItem> overdue,
    List<UpcomingItem> upcoming) {

}
