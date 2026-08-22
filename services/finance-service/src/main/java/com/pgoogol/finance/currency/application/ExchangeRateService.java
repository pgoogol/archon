package com.pgoogol.finance.currency.application;

import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.currency.domain.Currency;
import com.pgoogol.finance.currency.domain.ExchangeRate;
import com.pgoogol.finance.currency.domain.ExchangeRateId;
import com.pgoogol.finance.currency.domain.ExchangeRateProvider;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.RateSource;
import com.pgoogol.finance.currency.infrastructure.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Kursy wymiany: zapis, odczyt i wybór kursu właściwego dla daty.
 *
 * <p>Reguła wyboru jest jedna dla całego modułu: najnowszy kurs o dacie
 * nie późniejszej niż data księgowania. Data faktycznie użytego kursu wraca
 * w wyniku, bo bez niej nie da się odtworzyć, skąd wzięła się kwota bazowa.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyService currencyService;
    private final ExchangeRateProvider exchangeRateProvider;

    /** Kurs obowiązujący dla podanej daty księgowania. */
    public FxRate resolve(String code, LocalDate onDate) {

        Objects.requireNonNull(onDate, "onDate");
        Currency currency = currencyService.get(code);
        if (currencyService.isBase(currency.getCode())) {
            return FxRate.identity(onDate);
        }
        return exchangeRateRepository
            .findTopByIdCodeAndIdRateDateLessThanEqualOrderByIdRateDateDesc(
                currency.getCode(), onDate)
            .map(ExchangeRate::toFxRate)
            .orElseThrow(() -> new NotFoundException(ErrorCodes.EXCHANGE_RATE_NOT_FOUND,
                ExceptionMessageConstants.EXCHANGE_RATE_NOT_FOUND.formatted(
                    currency.getCode(), onDate)));
    }

    /** Kurs bieżący — do wyceny majątku na dziś, nigdy do przeliczania historii. */
    public FxRate current(String code) {

        return resolve(code, LocalDate.now());
    }

    public List<ExchangeRate> list(String code, LocalDate from, LocalDate to) {

        Currency currency = currencyService.get(code);
        LocalDate start = Objects.requireNonNullElse(from, LocalDate.now().minusMonths(1));
        LocalDate end = Objects.requireNonNullElse(to, LocalDate.now());
        requireOrderedRange(start, end);
        return exchangeRateRepository.findByIdCodeAndIdRateDateBetweenOrderByIdRateDateAsc(
            currency.getCode(), start, end);
    }

    @Transactional
    public ExchangeRate saveManual(String code, LocalDate rateDate, BigDecimal rate) {

        Currency currency = currencyService.get(code);
        if (currencyService.isBase(currency.getCode())) {
            throw new ValidationException(ErrorCodes.BASE_CURRENCY_RATE,
                ExceptionMessageConstants.BASE_CURRENCY_RATE.formatted(
                    currency.getCode()));
        }
        return upsert(currency.getCode(), new FxRate(rate, rateDate), RateSource.MANUAL);
    }

    /**
     * Pobranie kursów z zewnętrznego źródła za zakres dat. Waluta bazowa jest
     * pomijana. Dni bez publikacji nie dają wpisu i nie są błędem.
     */
    @Transactional
    public SyncResult sync(LocalDate from, LocalDate to, List<String> codes) {

        requireOrderedRange(from, to);
        List<String> targets = resolveTargets(codes);
        int saved = targets.stream()
            .mapToInt(code -> syncSingle(code, from, to))
            .sum();
        log.info("Synchronizacja kursów {}..{} dla {} walut: zapisano {} kursów",
            from, to, targets.size(), saved);
        return new SyncResult(from, to, saved, targets);
    }

    private int syncSingle(String code, LocalDate from, LocalDate to) {

        List<FxRate> fetched = exchangeRateProvider.fetchRates(code, from, to);
        fetched.forEach(rate -> upsert(code, rate, RateSource.NBP));
        return fetched.size();
    }

    private List<String> resolveTargets(List<String> codes) {

        if (Objects.isNull(codes) || codes.isEmpty()) {
            return currencyService.listAll().stream()
                .map(Currency::getCode)
                .filter(code -> !currencyService.isBase(code))
                .toList();
        }
        return codes.stream()
            .map(code -> currencyService.get(code).getCode())
            .filter(code -> !currencyService.isBase(code))
            .distinct()
            .toList();
    }

    private ExchangeRate upsert(String code, FxRate rate, RateSource source) {

        return exchangeRateRepository
            .findById(new ExchangeRateId(code, rate.rateDate()))
            .map(existing -> {
                existing.replaceRate(rate.rate(), source);
                return existing;
            })
            .orElseGet(() -> exchangeRateRepository.save(
                new ExchangeRate(code, rate.rateDate(), rate.rate(), source)));
    }

    private void requireOrderedRange(LocalDate from, LocalDate to) {

        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new ValidationException(ErrorCodes.INVALID_DATE_RANGE,
                ExceptionMessageConstants.INVALID_DATE_RANGE.formatted(from, to));
        }
    }

    /** Podsumowanie synchronizacji — ile kursów faktycznie wylądowało w bazie. */
    public record SyncResult(LocalDate from, LocalDate to, int savedCount, List<String> codes) {

    }
}
