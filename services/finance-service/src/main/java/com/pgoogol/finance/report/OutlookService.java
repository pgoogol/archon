package com.pgoogol.finance.report;

import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.application.ExchangeRateService;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.currency.domain.MoneyConverter;
import com.pgoogol.finance.transaction.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Spojrzenie w przód i wycena majątku na dziś.
 *
 * <p>To jedyne dwa miejsca w module, w których świadomie używamy kursu
 * <b>bieżącego</b>. Prognoza mówi o przyszłości, a ekspozycja walutowa o stanie
 * majątku dzisiaj — kurs sprzed roku nie ma w nich czego opisywać. Wszędzie
 * indziej obowiązuje kurs zapisany na transakcji.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OutlookService {

    private static final int MAX_HORIZON_DAYS = 365;
    private static final List<Long> NO_FILTER = List.of(-1L);

    private final JdbcClient jdbcClient;
    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;
    private final MoneyConverter moneyConverter;

    /** Zobowiązania do wskazanego dnia; przeterminowane osobno od nadchodzących. */
    public Outlook upcoming(int horizonDays, List<Long> accountIds) {

        LocalDate today = LocalDate.now();
        LocalDate horizonTo = today.plusDays(cappedHorizon(horizonDays));
        List<UpcomingItem> items = pendingUntil(horizonTo, accountIds, today);
        List<UpcomingItem> overdue = items.stream().filter(UpcomingItem::overdue).toList();
        List<UpcomingItem> ahead = items.stream().filter(item -> !item.overdue()).toList();
        return new Outlook(horizonTo, overdue, ahead);
    }

    /**
     * Prognoza salda dzień po dniu, w walucie bazowej.
     *
     * <p>Konto w walucie bez żadnego kursu przerywa prognozę błędem zamiast
     * cicho wypaść z sumy — brakujący kurs widać wtedy od razu, a nie po
     * miesiącu, gdy liczby przestaną się zgadzać.</p>
     */
    public Forecast forecast(int horizonDays, List<Long> accountIds) {

        LocalDate today = LocalDate.now();
        LocalDate horizonTo = today.plusDays(cappedHorizon(horizonDays));
        long starting = currentBaseBalance(accountIds, today);
        Map<LocalDate, Long> changes = dailyChanges(horizonTo, accountIds, today);

        List<ForecastPoint> points = new ArrayList<>();
        long balance = starting;
        LocalDate day = today;
        // dzień po dniu, bo wynik ma mieć punkt na każdą datę także wtedy,
        // gdy nic w niej nie wypada — wykres bez tych punktów kłamałby kształtem
        while (!day.isAfter(horizonTo)) {

            long change = changes.getOrDefault(day, 0L);
            balance = balance + change;
            points.add(new ForecastPoint(day, balance, change));
            day = day.plusDays(1);
        }
        return new Forecast(starting, List.copyOf(points));
    }

    /** Salda w podziale na waluty wraz z ich wartością bieżącą w walucie bazowej. */
    public Exposure currencyExposure() {

        LocalDate today = LocalDate.now();
        List<CurrentBalance> balances = currentBalances(List.of(), today);
        Map<String, Long> byCurrency = new LinkedHashMap<>();
        balances.forEach(balance ->
            byCurrency.merge(balance.currency(), balance.balanceMinor(), Long::sum));

        List<CurrencyExposureRow> rows = new ArrayList<>(byCurrency.size());
        long totalBase = 0L;
        for (Map.Entry<String, Long> entry : byCurrency.entrySet()) {

            CurrencyExposureRow row = exposureRow(entry.getKey(), entry.getValue());
            rows.add(row);
            totalBase = totalBase + Objects.requireNonNullElse(row.baseValueMinor(), 0L);
        }
        return new Exposure(List.copyOf(rows), totalBase);
    }

    /** Dzisiejsze salda kont, w walutach kont. */
    public List<CurrentBalance> currentBalances(List<Long> accountIds, LocalDate asOf) {

        boolean allAccounts = accountIds.isEmpty();
        return jdbcClient.sql(AnalyticsSql.ACCOUNT_BALANCES_NOW)
            .param("asOf", asOf)
            .param("allAccounts", allAccounts)
            .param("accountIds", filterOf(accountIds))
            .query(this::toCurrentBalance)
            .list();
    }

    /**
     * Salda kont z wyceną bieżącą. Brak kursu zostawia pustą wycenę zamiast
     * przerywać odczyt — pulpit ma pokazać saldo konta walutowego także wtedy,
     * gdy nikt jeszcze nie pobrał dla niego kursu.
     */
    public List<AccountValuation> valuations() {

        LocalDate today = LocalDate.now();
        List<CurrentBalance> balances = currentBalances(List.of(), today);
        return balances.stream().map(this::valuationOf).toList();
    }

    private AccountValuation valuationOf(CurrentBalance balance) {

        MinorUnits units = currencyService.minorUnitsOf(balance.currency());
        Long baseValue = baseValueOrNull(balance.balanceMinor(), balance.currency());
        return new AccountValuation(balance.accountId(), balance.accountName(),
            balance.currency(), units.scale(), balance.balanceMinor(), baseValue);
    }

    private CurrencyExposureRow exposureRow(String currency, long balanceMinor) {

        MinorUnits units = currencyService.minorUnitsOf(currency);
        Optional<FxRate> rate = exchangeRateService.currentIfKnown(currency);
        if (rate.isEmpty()) {

            return new CurrencyExposureRow(currency, units.scale(), balanceMinor, null, null, null);
        }
        FxRate known = rate.get();
        MinorUnits baseUnits = currencyService.baseMinorUnits();
        BigDecimal rateValue = known.rate();
        long baseValue = moneyConverter.convert(balanceMinor, units, rateValue, baseUnits);
        String plainRate = rateValue.toPlainString();
        return new CurrencyExposureRow(currency, units.scale(), balanceMinor, baseValue,
            plainRate, known.rateDate());
    }

    private Long baseValueOrNull(long amountMinor, String currency) {

        Optional<FxRate> rate = exchangeRateService.currentIfKnown(currency);
        if (rate.isEmpty()) {

            return null;
        }
        MinorUnits units = currencyService.minorUnitsOf(currency);
        MinorUnits baseUnits = currencyService.baseMinorUnits();
        FxRate known = rate.get();
        return moneyConverter.convert(amountMinor, units, known.rate(), baseUnits);
    }

    private long currentBaseBalance(List<Long> accountIds, LocalDate asOf) {

        List<CurrentBalance> balances = currentBalances(accountIds, asOf);
        long total = 0L;
        for (CurrentBalance balance : balances) {

            total = total + toBase(balance.balanceMinor(), balance.currency());
        }
        return total;
    }

    private Map<LocalDate, Long> dailyChanges(LocalDate horizonTo, List<Long> accountIds,
                                              LocalDate today) {

        List<UpcomingItem> items = pendingUntil(horizonTo, accountIds, today);
        Map<LocalDate, Long> changes = new LinkedHashMap<>();
        items.forEach(item -> changes.merge(dayOf(item, today), signedBase(item), Long::sum));
        return changes;
    }

    /**
     * Zaległa pozycja nie wypada w przeszłości — wchodzi do prognozy dzisiaj,
     * bo zapłacić ją trzeba i tak, a data sprzed tygodnia nie zmieści się na osi.
     */
    private LocalDate dayOf(UpcomingItem item, LocalDate today) {

        LocalDate dueDate = item.dueDate();
        if (dueDate.isBefore(today)) {

            return today;
        }
        return dueDate;
    }

    private long signedBase(UpcomingItem item) {

        long base = toBase(item.expectedAmountMinor(), item.currency());
        if (Objects.equals(item.type(), TransactionType.INCOME)) {

            return base;
        }
        return -base;
    }

    private long toBase(long amountMinor, String currency) {

        MinorUnits units = currencyService.minorUnitsOf(currency);
        MinorUnits baseUnits = currencyService.baseMinorUnits();
        FxRate rate = exchangeRateService.current(currency);
        return moneyConverter.convert(amountMinor, units, rate.rate(), baseUnits);
    }

    private List<UpcomingItem> pendingUntil(LocalDate horizonTo, List<Long> accountIds,
                                            LocalDate today) {

        boolean allAccounts = accountIds.isEmpty();
        return jdbcClient.sql(AnalyticsSql.UPCOMING)
            .param("today", today)
            .param("horizonTo", horizonTo)
            .param("allAccounts", allAccounts)
            .param("accountIds", filterOf(accountIds))
            .query(this::toUpcomingItem)
            .list();
    }

    private int cappedHorizon(int horizonDays) {

        return Math.clamp(horizonDays, 1, MAX_HORIZON_DAYS);
    }

    private List<Long> filterOf(List<Long> ids) {

        if (ids.isEmpty()) {

            return NO_FILTER;
        }
        return ids;
    }

    private UpcomingItem toUpcomingItem(ResultSet rows, int rowNum) throws SQLException {

        LocalDate dueDate = rows.getObject("due_date", LocalDate.class);
        TransactionType type = TransactionType.valueOf(rows.getString("rule_type"));
        return new UpcomingItem(rows.getLong("occurrence_id"), rows.getLong("rule_id"),
            rows.getString("rule_name"), rows.getLong("account_id"),
            rows.getString("account_name"), dueDate, rows.getLong("expected_amount_minor"),
            rows.getString("currency"), type, rows.getBoolean("overdue"));
    }

    private CurrentBalance toCurrentBalance(ResultSet rows, int rowNum) throws SQLException {

        return new CurrentBalance(rows.getLong("account_id"), rows.getString("account_name"),
            rows.getString("currency"), rows.getLong("balance_minor"));
    }

    public record CurrentBalance(
        long accountId,
        String accountName,
        String currency,
        long balanceMinor) {

    }

    public record Outlook(
        LocalDate horizonTo,
        List<UpcomingItem> overdue,
        List<UpcomingItem> upcoming) {

    }

    public record Forecast(long startingBalanceMinor, List<ForecastPoint> points) {

    }

    public record Exposure(List<CurrencyExposureRow> rows, long totalBaseMinor) {

    }
}
