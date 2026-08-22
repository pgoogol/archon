package com.pgoogol.finance.currency.application;

import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.currency.domain.Currency;
import com.pgoogol.finance.currency.domain.FinanceProperties;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.currency.infrastructure.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Słownik walut. Każde pytanie o skalę waluty przechodzi tędy — nigdzie indziej
 * w module nie wolno założyć, że kwota ma dwa miejsca po przecinku.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final FinanceProperties financeProperties;

    public List<Currency> listAll() {

        return currencyRepository.findAllByOrderByCodeAsc();
    }

    public Currency get(String code) {

        String normalized = normalize(code);
        return currencyRepository.findById(normalized)
            .orElseThrow(() -> new NotFoundException(ErrorCodes.CURRENCY_NOT_FOUND,
                ExceptionMessageConstants.CURRENCY_NOT_FOUND.formatted(normalized)));
    }

    public MinorUnits minorUnitsOf(String code) {

        Currency currency = get(code);
        return currency.minorUnits();
    }

    public String baseCurrency() {

        return financeProperties.baseCurrency();
    }

    public MinorUnits baseMinorUnits() {

        return minorUnitsOf(financeProperties.baseCurrency());
    }

    public boolean isBase(String code) {

        return financeProperties.isBase(normalize(code));
    }

    @Transactional
    public Currency add(String code, String name, int minorUnit) {

        String normalized = normalize(code);
        if (currencyRepository.existsById(normalized)) {
            throw new ConflictException(ErrorCodes.CURRENCY_EXISTS,
                ExceptionMessageConstants.CURRENCY_EXISTS.formatted(normalized));
        }
        return currencyRepository.save(new Currency(normalized, name, minorUnit));
    }

    private String normalize(String code) {

        Objects.requireNonNull(code, "code");
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
