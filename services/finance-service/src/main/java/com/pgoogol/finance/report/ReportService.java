package com.pgoogol.finance.report;

import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Zestawienia liczone zapytaniem, nie pętlą po encjach.
 *
 * <p>Ten pakiet celowo nie ma encji JPA ani repozytoriów Spring Data — agregaty
 * przechodzą przez wiele domen naraz i nie odpowiadają żadnemu agregatowi
 * domenowemu, więc encja byłaby tu kosztem bez zysku. Pilnuje tego
 * {@code ArchitectureTest}.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

    /**
     * Podstawiane, gdy filtr jest pusty. Lista musi mieć element, bo
     * {@code in ()} nie jest poprawnym SQL-em — sam warunek i tak jest wtedy
     * wyłączony flagą {@code allAccounts} / {@code allCategories}.
     */
    private static final List<Long> NO_FILTER = List.of(-1L);

    private final JdbcClient jdbcClient;

    public List<ByCategoryRow> byCategory(ReportRange range, CategoryDirection direction) {

        requireValidRange(range);
        Objects.requireNonNull(direction, "direction");
        return flowStatement(ReportSql.BY_CATEGORY, range, direction)
            .query(this::toByCategoryRow)
            .list();
    }

    public List<PeriodTotal> byCategoryTotals(ReportRange range, CategoryDirection direction) {

        requireValidRange(range);
        Objects.requireNonNull(direction, "direction");
        return flowStatement(ReportSql.BY_CATEGORY_TOTALS, range, direction)
            .query(this::toPeriodTotal)
            .list();
    }

    public List<CashflowRow> cashflow(ReportRange range) {

        requireValidRange(range);
        return flowStatement(ReportSql.CASHFLOW, range, null)
            .query(this::toCashflowRow)
            .list();
    }

    public List<AccountBalanceRow> balances(ReportRange range) {

        requireValidRange(range);
        return jdbcClient.sql(ReportSql.BALANCES)
            .param("granularity", range.granularity().datePart())
            .param("from", range.from())
            .param("to", range.to())
            .param("allAccounts", range.allAccounts())
            .param("accountIds", accountIds(range))
            .query(this::toAccountBalanceRow)
            .list();
    }

    /**
     * Wspólny komplet parametrów raportów przepływów. Każdy z nich pyta o to
     * samo okno i te same filtry — różni się wyłącznie tekstem zapytania.
     */
    private JdbcClient.StatementSpec flowStatement(String sql, ReportRange range,
                                                   @Nullable CategoryDirection direction) {

        String directionName = null;
        if (Objects.nonNull(direction)) {

            directionName = direction.name();
        }
        return jdbcClient.sql(sql)
            .param("granularity", range.granularity().datePart())
            .param("from", range.from())
            .param("to", range.to())
            .param("allAccounts", range.allAccounts())
            .param("accountIds", accountIds(range))
            .param("allCategories", range.allCategories())
            .param("categoryIds", categoryIds(range))
            .param("direction", directionName);
    }

    private List<Long> accountIds(ReportRange range) {

        if (range.allAccounts()) {

            return NO_FILTER;
        }
        return range.accountIds();
    }

    private List<Long> categoryIds(ReportRange range) {

        if (range.allCategories()) {

            return NO_FILTER;
        }
        return range.categoryIds();
    }

    private ByCategoryRow toByCategoryRow(ResultSet rows, int rowNum) throws SQLException {

        LocalDate period = rows.getObject("period", LocalDate.class);
        Long parentCategoryId = rows.getObject("parent_category_id", Long.class);
        return new ByCategoryRow(period, rows.getLong("category_id"),
            rows.getString("category_name"), parentCategoryId, rows.getLong("amount_minor"),
            rows.getLong("own_amount_minor"), rows.getInt("transaction_count"));
    }

    private PeriodTotal toPeriodTotal(ResultSet rows, int rowNum) throws SQLException {

        LocalDate period = rows.getObject("period", LocalDate.class);
        return new PeriodTotal(period, rows.getLong("amount_minor"));
    }

    private CashflowRow toCashflowRow(ResultSet rows, int rowNum) throws SQLException {

        LocalDate period = rows.getObject("period", LocalDate.class);
        long income = rows.getLong("income_minor");
        long expense = rows.getLong("expense_minor");
        return new CashflowRow(period, income, expense, income - expense);
    }

    private AccountBalanceRow toAccountBalanceRow(ResultSet rows, int rowNum) throws SQLException {

        LocalDate period = rows.getObject("period", LocalDate.class);
        return new AccountBalanceRow(period, rows.getLong("account_id"),
            rows.getString("account_name"), rows.getString("currency"),
            rows.getLong("balance_minor"));
    }

    private void requireValidRange(ReportRange range) {

        Objects.requireNonNull(range, "range");
        LocalDate from = range.from();
        LocalDate to = range.to();
        if (from.isAfter(to)) {

            throw new ValidationException(ErrorCodes.INVALID_DATE_RANGE,
                ExceptionMessageConstants.INVALID_DATE_RANGE.formatted(from, to));
        }
    }
}
