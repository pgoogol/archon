package com.pgoogol.finance.report;

import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Raporty analityczne: porównania okresów, największe wydatki, podział na
 * koszty stałe i zmienne oraz macierz roczna.
 *
 * <p>Procenty liczymy {@link BigDecimal} i zwracamy tekstem — udział i zmiana
 * nie są kwotą, ale przez JSON-owy {@code number} przeszłyby jako
 * {@code double}, a w tym module żadna liczba dziesiętna tego nie robi.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MONTHS_IN_AVERAGE = 12;
    private static final int MAX_LIMIT = 50;
    private static final List<Long> NO_FILTER = List.of(-1L);

    private final JdbcClient jdbcClient;

    /**
     * Porównanie okresu z poprzednim oknem tej samej długości i ze średnią
     * miesięczną z dwunastu miesięcy przed nim.
     */
    public Comparison comparison(ReportRange range, CategoryDirection direction) {

        requireValidRange(range);
        Objects.requireNonNull(direction, "direction");
        ComparisonWindows windows = windowsFor(range);
        List<CategoryComparisonRow> rows = jdbcClient.sql(AnalyticsSql.COMPARISON)
            .param("windowFrom", windows.windowFrom())
            .param("windowTo", range.to())
            .param("from", range.from())
            .param("to", range.to())
            .param("previousFrom", windows.previousFrom())
            .param("previousTo", windows.previousTo())
            .param("averageFrom", windows.averageFrom())
            .param("averageTo", windows.averageTo())
            .param("allAccounts", range.allAccounts())
            .param("accountIds", filterOf(range.accountIds()))
            .param("allCategories", range.allCategories())
            .param("categoryIds", filterOf(range.categoryIds()))
            .param("direction", direction.name())
            .query(this::toComparisonRow)
            .list();
        return new Comparison(windows.previousFrom(), windows.previousTo(), rows);
    }

    public List<TopExpenseRow> topExpenses(ReportRange range, int limit) {

        requireValidRange(range);
        return topStatement(AnalyticsSql.TOP_EXPENSES, range, limit)
            .query(this::toTopExpenseRow)
            .list();
    }

    public List<TopCounterpartyRow> topCounterparties(ReportRange range, int limit) {

        requireValidRange(range);
        return topStatement(AnalyticsSql.TOP_COUNTERPARTIES, range, limit)
            .query(this::toTopCounterpartyRow)
            .list();
    }

    public List<FixedVsVariableRow> fixedVsVariable(ReportRange range) {

        requireValidRange(range);
        return jdbcClient.sql(AnalyticsSql.FIXED_VS_VARIABLE)
            .param("granularity", range.granularity().datePart())
            .param("from", range.from())
            .param("to", range.to())
            .param("allAccounts", range.allAccounts())
            .param("accountIds", filterOf(range.accountIds()))
            .query(this::toFixedVsVariableRow)
            .list();
    }

    /**
     * Macierz kategoria razy miesiąc. Zapytanie zwraca trójki
     * (kategoria, miesiąc, kwota); w wiersze o dwunastu kolumnach układa je
     * aplikacja — to prezentacja, nie agregacja.
     */
    public YearlyMatrix yearlyMatrix(int year, ReportRange range, CategoryDirection direction) {

        Objects.requireNonNull(direction, "direction");
        List<MatrixCell> cells = jdbcClient.sql(AnalyticsSql.YEARLY_MATRIX)
            .param("windowFrom", range.from())
            .param("windowTo", range.to())
            .param("allAccounts", range.allAccounts())
            .param("accountIds", filterOf(range.accountIds()))
            .param("allCategories", range.allCategories())
            .param("categoryIds", filterOf(range.categoryIds()))
            .param("direction", direction.name())
            .query(this::toMatrixCell)
            .list();
        return pivot(year, cells);
    }

    private JdbcClient.StatementSpec topStatement(String sql, ReportRange range, int limit) {

        return jdbcClient.sql(sql)
            .param("from", range.from())
            .param("to", range.to())
            .param("allAccounts", range.allAccounts())
            .param("accountIds", filterOf(range.accountIds()))
            .param("allCategories", range.allCategories())
            .param("categoryIds", filterOf(range.categoryIds()))
            .param("limit", cappedLimit(limit));
    }

    private YearlyMatrix pivot(int year, List<MatrixCell> cells) {

        Map<Long, long[]> monthsByCategory = new LinkedHashMap<>();
        Map<Long, String> nameByCategory = new LinkedHashMap<>();
        cells.forEach(cell -> collect(cell, monthsByCategory, nameByCategory));

        long[] monthlyTotals = new long[12];
        List<YearlyMatrixRow> rows = new ArrayList<>(monthsByCategory.size());
        monthsByCategory.forEach((categoryId, months) ->
            rows.add(matrixRow(categoryId, nameByCategory.get(categoryId), months, monthlyTotals)));
        long total = 0L;
        for (long monthly : monthlyTotals) {

            total = total + monthly;
        }
        return new YearlyMatrix(year, List.copyOf(rows), boxed(monthlyTotals), total);
    }

    private void collect(MatrixCell cell, Map<Long, long[]> monthsByCategory,
                         Map<Long, String> nameByCategory) {

        long[] months = monthsByCategory.computeIfAbsent(cell.categoryId(), key -> new long[12]);
        months[cell.monthIndex() - 1] = cell.amountMinor();
        nameByCategory.put(cell.categoryId(), cell.categoryName());
    }

    private YearlyMatrixRow matrixRow(long categoryId, String categoryName, long[] months,
                                      long[] monthlyTotals) {

        long total = 0L;
        for (int month = 0; month < months.length; month++) {

            total = total + months[month];
            monthlyTotals[month] = monthlyTotals[month] + months[month];
        }
        return new YearlyMatrixRow(categoryId, categoryName, boxed(months), total);
    }

    private List<Long> boxed(long[] values) {

        List<Long> boxed = new ArrayList<>(values.length);
        for (long value : values) {

            boxed.add(value);
        }
        return List.copyOf(boxed);
    }

    /**
     * Poprzednie okno ma tę samą długość i kończy się dzień przed początkiem
     * bieżącego. Średnia liczy się z dwunastu pełnych miesięcy poprzedzających
     * miesiąc, w którym okres się zaczyna — miesiąc w połowie zaniżałby ją.
     */
    private ComparisonWindows windowsFor(ReportRange range) {

        long lengthInDays = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        LocalDate previousTo = range.from().minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(lengthInDays - 1);
        YearMonth startMonth = YearMonth.from(range.from());
        YearMonth averageLastMonth = startMonth.minusMonths(1);
        YearMonth averageFirstMonth = averageLastMonth.minusMonths(MONTHS_IN_AVERAGE - 1L);
        LocalDate averageFrom = averageFirstMonth.atDay(1);
        LocalDate averageTo = averageLastMonth.atEndOfMonth();
        LocalDate windowFrom = earliest(previousFrom, averageFrom);
        return new ComparisonWindows(windowFrom, previousFrom, previousTo, averageFrom, averageTo);
    }

    private LocalDate earliest(LocalDate first, LocalDate second) {

        if (first.isBefore(second)) {

            return first;
        }
        return second;
    }

    private int cappedLimit(int limit) {

        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    private List<Long> filterOf(List<Long> ids) {

        if (ids.isEmpty()) {

            return NO_FILTER;
        }
        return ids;
    }

    private CategoryComparisonRow toComparisonRow(ResultSet rows, int rowNum) throws SQLException {

        long current = rows.getLong("current_minor");
        long previous = rows.getLong("previous_minor");
        String change = changePercent(current, previous);
        return new CategoryComparisonRow(rows.getLong("category_id"),
            rows.getString("category_name"), current, previous,
            rows.getLong("monthly_average_minor"), change);
    }

    /** {@code null} zamiast nieskończoności: wzrost z zera nie ma procentu. */
    private String changePercent(long current, long previous) {

        if (previous == 0L) {

            return null;
        }
        BigDecimal difference = BigDecimal.valueOf(current - previous);
        BigDecimal base = BigDecimal.valueOf(previous);
        BigDecimal percent = difference.multiply(BigDecimal.valueOf(100))
            .divide(base, 2, RoundingMode.HALF_UP);
        return percent.toPlainString();
    }

    private TopExpenseRow toTopExpenseRow(ResultSet rows, int rowNum) throws SQLException {

        LocalDate bookedOn = rows.getObject("booked_on", LocalDate.class);
        return new TopExpenseRow(rows.getLong("transaction_id"), bookedOn,
            rows.getLong("amount_minor"), rows.getString("description"),
            rows.getString("counterparty"), rows.getString("category_name"),
            rows.getString("account_name"));
    }

    private TopCounterpartyRow toTopCounterpartyRow(ResultSet rows, int rowNum)
            throws SQLException {

        return new TopCounterpartyRow(rows.getString("counterparty"),
            rows.getLong("amount_minor"), rows.getInt("transaction_count"));
    }

    private FixedVsVariableRow toFixedVsVariableRow(ResultSet rows, int rowNum)
            throws SQLException {

        LocalDate period = rows.getObject("period", LocalDate.class);
        long fixed = rows.getLong("fixed_minor");
        long variable = rows.getLong("variable_minor");
        String share = sharePercent(fixed, variable);
        return new FixedVsVariableRow(period, fixed, variable, share);
    }

    private String sharePercent(long fixed, long variable) {

        long total = fixed + variable;
        if (total == 0L) {

            return null;
        }
        BigDecimal share = BigDecimal.valueOf(fixed).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return share.toPlainString();
    }

    private MatrixCell toMatrixCell(ResultSet rows, int rowNum) throws SQLException {

        return new MatrixCell(rows.getLong("category_id"), rows.getString("category_name"),
            rows.getInt("month_index"), rows.getLong("amount_minor"));
    }

    private void requireValidRange(ReportRange range) {

        Objects.requireNonNull(range, "range");
        if (range.from().isAfter(range.to())) {

            throw new ValidationException(ErrorCodes.INVALID_DATE_RANGE,
                ExceptionMessageConstants.INVALID_DATE_RANGE.formatted(range.from(), range.to()));
        }
    }

    /** Okna porównania wyliczone raz, żeby zapytanie dostało gotowe daty. */
    private record ComparisonWindows(
        LocalDate windowFrom,
        LocalDate previousFrom,
        LocalDate previousTo,
        LocalDate averageFrom,
        LocalDate averageTo) {

    }

    private record MatrixCell(
        long categoryId,
        String categoryName,
        int monthIndex,
        long amountMinor) {

    }

    public record Comparison(
        LocalDate previousFrom,
        LocalDate previousTo,
        List<CategoryComparisonRow> rows) {

    }

    public record YearlyMatrix(
        int year,
        List<YearlyMatrixRow> rows,
        List<Long> monthlyTotalsMinor,
        long totalMinor) {

    }
}
