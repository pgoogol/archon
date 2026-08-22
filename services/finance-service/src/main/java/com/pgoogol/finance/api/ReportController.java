package com.pgoogol.finance.api;

import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.report.AccountBalanceRow;
import com.pgoogol.finance.report.ByCategoryRow;
import com.pgoogol.finance.report.CashflowRow;
import com.pgoogol.finance.report.Granularity;
import com.pgoogol.finance.report.PeriodTotal;
import com.pgoogol.finance.report.ReportRange;
import com.pgoogol.finance.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/finance/api/v1/reports")
@Tag(name = "reports", description = "Zestawienia i raporty")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final CurrencyService currencyService;

    @GetMapping("/by-category")
    @Operation(summary = "Wydatki w podziale na kategorie i okresy",
        description = """
            Kategoria nadrzędna zawiera sumę swoich podkategorii, a same \
            podkategorie są w odpowiedzi obok niej — dzięki temu klient rozwija \
            gałąź bez drugiego zapytania.""")
    public ByCategoryReportResponse reportByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Granularity granularity,
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) CategoryDirection direction) {

        ReportRange range = range(from, to, granularity, accountIds, categoryIds);
        CategoryDirection chosen = directionOrExpense(direction);
        List<ByCategoryRow> rows = reportService.byCategory(range, chosen);
        List<PeriodTotal> totals = reportService.byCategoryTotals(range, chosen);
        String baseCurrency = currencyService.baseCurrency();
        return new ByCategoryReportResponse(baseCurrency, rows, totals);
    }

    @GetMapping("/cashflow")
    @Operation(summary = "Przychody, wydatki i bilans w podziale na okresy")
    public CashflowReportResponse reportCashflow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Granularity granularity,
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) List<Long> categoryIds) {

        ReportRange range = range(from, to, granularity, accountIds, categoryIds);
        List<CashflowRow> rows = reportService.cashflow(range);
        String baseCurrency = currencyService.baseCurrency();
        return new CashflowReportResponse(baseCurrency, rows);
    }

    @GetMapping("/balances")
    @Operation(summary = "Saldo każdego konta na koniec każdego okresu",
        description = """
            Saldo podawane jest w walucie konta. Nie przeliczamy go na walutę \
            bazową, bo dla okresu zamkniętego w przeszłości nie ma jednego \
            uczciwego kursu: bieżący zmieniałby historię, a historyczny nie \
            opisuje dzisiejszego stanu majątku.""")
    public BalancesReportResponse reportBalances(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Granularity granularity,
            @RequestParam(required = false) List<Long> accountIds) {

        ReportRange range = range(from, to, granularity, accountIds, List.of());
        List<AccountBalanceRow> rows = reportService.balances(range);
        return new BalancesReportResponse(rows);
    }

    private ReportRange range(LocalDate from, LocalDate to, Granularity granularity,
                              List<Long> accountIds, List<Long> categoryIds) {

        Granularity chosen = granularityOrMonth(granularity);
        List<Long> accounts = emptyIfNull(accountIds);
        List<Long> categories = emptyIfNull(categoryIds);
        return new ReportRange(from, to, chosen, accounts, categories);
    }

    private Granularity granularityOrMonth(Granularity granularity) {

        return Objects.requireNonNullElse(granularity, Granularity.MONTH);
    }

    private CategoryDirection directionOrExpense(CategoryDirection direction) {

        return Objects.requireNonNullElse(direction, CategoryDirection.EXPENSE);
    }

    private List<Long> emptyIfNull(List<Long> ids) {

        return Objects.requireNonNullElse(ids, List.of());
    }
}
