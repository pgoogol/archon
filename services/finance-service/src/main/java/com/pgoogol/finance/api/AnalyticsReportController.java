package com.pgoogol.finance.api;

import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.report.AccountValuation;
import com.pgoogol.finance.report.AnalyticsService;
import com.pgoogol.finance.report.CashflowRow;
import com.pgoogol.finance.report.FixedVsVariableRow;
import com.pgoogol.finance.report.Granularity;
import com.pgoogol.finance.report.OutlookService;
import com.pgoogol.finance.report.ReportRange;
import com.pgoogol.finance.report.ReportService;
import com.pgoogol.finance.report.TopCounterpartyRow;
import com.pgoogol.finance.report.TopExpenseRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Raporty analityczne i pulpit. Osobno od {@link ReportController}, bo trzy
 * zestawienia podstawowe i osiem analitycznych w jednej klasie przekroczyłyby
 * rozsądną długość — ścieżka i tag pozostają wspólne.
 */
@RestController
@RequestMapping("/finance/api/v1/reports")
@Tag(name = "reports", description = "Zestawienia i raporty")
@RequiredArgsConstructor
public class AnalyticsReportController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_HORIZON_DAYS = 30;

    private final AnalyticsService analyticsService;
    private final OutlookService outlookService;
    private final ReportService reportService;
    private final CurrencyService currencyService;

    @GetMapping("/comparison")
    @Operation(summary = "Okres wobec poprzedniego i wobec średniej z dwunastu miesięcy",
        description = """
            Poprzedni okres to okno tej samej długości bezpośrednio przed \
            podanym. Średnia liczona jest z dwunastu pełnych miesięcy \
            poprzedzających miesiąc, w którym zaczyna się okres.""")
    public ComparisonReportResponse reportComparison(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) CategoryDirection direction) {

        ReportRange range = range(from, to, Granularity.MONTH, accountIds, categoryIds);
        CategoryDirection chosen = directionOrExpense(direction);
        AnalyticsService.Comparison comparison = analyticsService.comparison(range, chosen);
        String baseCurrency = currencyService.baseCurrency();
        return new ComparisonReportResponse(baseCurrency, comparison.previousFrom(),
            comparison.previousTo(), comparison.rows());
    }

    @GetMapping("/top-spend")
    @Operation(summary = "Największe pojedyncze wydatki i najczęstsi kontrahenci")
    public TopSpendReportResponse reportTopSpend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(defaultValue = StringUtils.EMPTY + DEFAULT_LIMIT) int limit) {

        ReportRange range = range(from, to, Granularity.MONTH, accountIds, categoryIds);
        List<TopExpenseRow> transactions = analyticsService.topExpenses(range, limit);
        List<TopCounterpartyRow> counterparties = analyticsService.topCounterparties(range, limit);
        String baseCurrency = currencyService.baseCurrency();
        return new TopSpendReportResponse(baseCurrency, transactions, counterparties);
    }

    @GetMapping("/fixed-vs-variable")
    @Operation(summary = "Udział kosztów mających pokrycie w regule cyklicznej",
        description = """
            Za koszt stały uznajemy wyłącznie wydatek powiązany z pozycją \
            terminarza — czyli taki, który ktoś potwierdził jako płatność \
            rachunku cyklicznego. Zgadywanie po kategorii dawałoby liczbę \
            wyglądającą wiarygodnie i nieprawdziwą.""")
    public FixedVsVariableReportResponse reportFixedVsVariable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Granularity granularity,
            @RequestParam(required = false) List<Long> accountIds) {

        ReportRange range = range(from, to, granularity, accountIds, List.of());
        List<FixedVsVariableRow> rows = analyticsService.fixedVsVariable(range);
        String baseCurrency = currencyService.baseCurrency();
        return new FixedVsVariableReportResponse(baseCurrency, rows);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Zobowiązania na najbliższe dni wraz z przeterminowanymi")
    public UpcomingReportResponse reportUpcoming(
            @RequestParam(defaultValue = StringUtils.EMPTY + DEFAULT_HORIZON_DAYS) int horizonDays) {

        OutlookService.Outlook outlook = outlookService.upcoming(horizonDays, List.of());
        return new UpcomingReportResponse(outlook.horizonTo(), outlook.overdue(),
            outlook.upcoming());
    }

    @GetMapping("/forecast")
    @Operation(summary = "Prognoza salda dzień po dniu",
        description = """
            Prognoza jest jedynym raportem poza ekspozycją walutową, który używa \
            kursu bieżącego — mówi o przyszłości, a nie o historii. Punktem \
            wyjścia jest dzisiejsze saldo, a ruchy biorą się z pozycji \
            terminarza o statusie PENDING.""")
    public ForecastReportResponse reportForecast(
            @RequestParam(defaultValue = StringUtils.EMPTY + DEFAULT_HORIZON_DAYS) int horizonDays,
            @RequestParam(required = false) List<Long> accountIds) {

        List<Long> accounts = emptyIfNull(accountIds);
        OutlookService.Forecast forecast = outlookService.forecast(horizonDays, accounts);
        String baseCurrency = currencyService.baseCurrency();
        return new ForecastReportResponse(baseCurrency, forecast.startingBalanceMinor(),
            forecast.points());
    }

    @GetMapping("/yearly-matrix")
    @Operation(summary = "Kategoria razy miesiąc za jeden rok, z sumami")
    public YearlyMatrixReportResponse reportYearlyMatrix(
            @RequestParam int year,
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) CategoryDirection direction) {

        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        ReportRange range = range(from, to, Granularity.MONTH, accountIds, categoryIds);
        CategoryDirection chosen = directionOrExpense(direction);
        AnalyticsService.YearlyMatrix matrix = analyticsService.yearlyMatrix(year, range, chosen);
        String baseCurrency = currencyService.baseCurrency();
        return new YearlyMatrixReportResponse(baseCurrency, matrix.year(), matrix.rows(),
            matrix.monthlyTotalsMinor(), matrix.totalMinor());
    }

    @GetMapping("/currency-exposure")
    @Operation(summary = "Salda w podziale na waluty i ich wartość bieżąca",
        description = """
            Jedyne miejsce obok prognozy, w którym świadomie używamy kursu \
            bieżącego — raport pokazuje stan majątku na dziś, a nie historię \
            operacji.""")
    public CurrencyExposureReportResponse reportCurrencyExposure() {

        OutlookService.Exposure exposure = outlookService.currencyExposure();
        String baseCurrency = currencyService.baseCurrency();
        return new CurrencyExposureReportResponse(baseCurrency, exposure.rows(),
            exposure.totalBaseMinor());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Pulpit — salda, bilans miesiąca i najbliższe płatności")
    public DashboardResponse reportDashboard() {

        YearMonth month = YearMonth.now();
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        ReportRange monthRange =
            range(monthStart, monthEnd, Granularity.MONTH, List.of(), List.of());

        List<CashflowRow> cashflow = reportService.cashflow(monthRange);
        CashflowRow current = firstOrEmpty(cashflow, monthStart);
        List<AccountValuation> balances = outlookService.valuations();
        OutlookService.Outlook outlook = outlookService.upcoming(DEFAULT_HORIZON_DAYS, List.of());
        String baseCurrency = currencyService.baseCurrency();
        return new DashboardResponse(baseCurrency, monthStart, balances, current.incomeMinor(),
            current.expenseMinor(), current.netMinor(), outlook.overdue(), outlook.upcoming());
    }

    /** Miesiąc bez żadnej operacji to zera, nie brak sekcji na pulpicie. */
    private CashflowRow firstOrEmpty(List<CashflowRow> rows, LocalDate period) {

        if (rows.isEmpty()) {

            return new CashflowRow(period, 0L, 0L, 0L);
        }
        return rows.getFirst();
    }

    private ReportRange range(LocalDate from, LocalDate to, Granularity granularity,
                              List<Long> accountIds, List<Long> categoryIds) {

        Granularity chosen = Objects.requireNonNullElse(granularity, Granularity.MONTH);
        List<Long> accounts = emptyIfNull(accountIds);
        List<Long> categories = emptyIfNull(categoryIds);
        return new ReportRange(from, to, chosen, accounts, categories);
    }

    private CategoryDirection directionOrExpense(CategoryDirection direction) {

        return Objects.requireNonNullElse(direction, CategoryDirection.EXPENSE);
    }

    private List<Long> emptyIfNull(List<Long> ids) {

        return Objects.requireNonNullElse(ids, List.of());
    }
}
