package com.pgoogol.finance;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountType;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.currency.domain.Currency;
import com.pgoogol.finance.currency.domain.ExchangeRate;
import com.pgoogol.finance.currency.domain.RateSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Object Mother dla encji finansowych. Jedyna dozwolona klasa statyczna w tym
 * module — reguła stylu robi wyjątek dla fixtures testowych.
 *
 * <p>Identyfikatory ustawiamy refleksją, bo w produkcji nadaje je baza, a testy
 * jednostkowe muszą odróżniać dwa konta od siebie.</p>
 */
public final class FinanceFixtures {

    public static final String PLN = "PLN";
    public static final String EUR = "EUR";

    private FinanceFixtures() {

    }

    public static Currency zloty() {
        return new Currency(PLN, "złoty polski", 2);
    }

    public static Currency euro() {
        return new Currency(EUR, "euro", 2);
    }

    public static Currency yen() {
        return new Currency("JPY", "jen japoński", 0);
    }

    public static Account account(long id, String name, String currency) {

        Account account = new Account(name, AccountType.BANK, currency, null, 0L,
            LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    public static Category category(long id, String name, CategoryDirection direction) {

        Category category = new Category(null, name, direction);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    public static Category childCategory(long id, Category parent, String name) {

        Category category = new Category(parent, name, parent.getDirection());
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    public static ExchangeRate rate(String code, LocalDate date, String value) {
        return new ExchangeRate(code, date, new BigDecimal(value), RateSource.NBP);
    }
}
