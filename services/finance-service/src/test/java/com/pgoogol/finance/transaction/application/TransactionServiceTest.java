package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.application.ExchangeRateService;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.currency.domain.MoneyConverter;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import com.pgoogol.finance.transaction.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 14);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 15);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private ExchangeRateService exchangeRateService;

    private TransactionService transactionService;

    private final Account euroAccount =
        FinanceFixtures.account(1L, "Walutowe", FinanceFixtures.EUR);
    private final Category expenseCategory =
        FinanceFixtures.category(10L, "Jedzenie", CategoryDirection.EXPENSE);

    @BeforeEach
    void setUp() {

        transactionService = new TransactionService(transactionRepository, accountService,
            categoryService, currencyService, exchangeRateService, new MoneyConverter(),
            new TransactionValidator());
        when(accountService.get(1L)).thenReturn(euroAccount);
        when(categoryService.get(10L)).thenReturn(expenseCategory);
        when(currencyService.minorUnitsOf(FinanceFixtures.EUR)).thenReturn(new MinorUnits(2));
        when(currencyService.baseMinorUnits()).thenReturn(new MinorUnits(2));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("kurs i kwota bazowa zapisują się na transakcji w chwili jej powstania")
    void create_whenAmountIsForeign_storesRateAndBaseAmount() {

        // given: sobota, więc obowiązuje kurs z piątku
        when(exchangeRateService.resolve(FinanceFixtures.EUR, SATURDAY))
            .thenReturn(new FxRate(new BigDecimal("4.3215"), FRIDAY));

        // when
        Transaction transaction = transactionService.create(expenseOn(SATURDAY));

        // then
        assertThat(transaction.getBaseAmountMinor()).isEqualTo(43_215L);
        assertThat(transaction.getFxRate()).isEqualByComparingTo("4.3215");
        assertThat(transaction.getFxRateDate()).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("zmiana daty księgowania przelicza kwotę bazową kursem z nowej daty")
    void update_whenBookingDateChanges_recalculatesBaseAmount() {

        // given
        when(exchangeRateService.resolve(FinanceFixtures.EUR, SATURDAY))
            .thenReturn(new FxRate(new BigDecimal("4.3215"), FRIDAY));
        Transaction existing = transactionService.create(expenseOn(SATURDAY));
        when(transactionRepository.findDetailedById(5L)).thenReturn(Optional.of(existing));

        LocalDate laterDay = LocalDate.of(2026, 8, 20);
        when(exchangeRateService.resolve(FinanceFixtures.EUR, laterDay))
            .thenReturn(new FxRate(new BigDecimal("4.5000"), laterDay));

        // when
        Transaction updated = transactionService.update(5L, expenseOn(laterDay));

        // then
        assertThat(updated.getBaseAmountMinor()).isEqualTo(45_000L);
        assertThat(updated.getFxRateDate()).isEqualTo(laterDay);
    }

    @Test
    @DisplayName("waluta transakcji bierze się z konta, nie z żądania")
    void create_whenSaved_usesAccountCurrency() {

        // given
        when(exchangeRateService.resolve(FinanceFixtures.EUR, SATURDAY))
            .thenReturn(new FxRate(new BigDecimal("4.3215"), FRIDAY));

        // when
        Transaction transaction = transactionService.create(expenseOn(SATURDAY));

        // then
        assertThat(transaction.getCurrency()).isEqualTo(FinanceFixtures.EUR);
        assertThat(transaction.getAccount()).isSameAs(euroAccount);
    }

    private TransactionCommand expenseOn(LocalDate day) {

        return new TransactionCommand(TransactionType.EXPENSE, day, 10_000L, FinanceFixtures.EUR,
            null, null, 1L, null, null, 10L, "obiad", null);
    }
}
