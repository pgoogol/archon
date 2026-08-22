package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.application.ExchangeRateService;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.MoneyConverter;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.infrastructure.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Zapis i odczyt transakcji.
 *
 * <p>Kurs wyznacza się w chwili zapisu i zostaje na transakcji na zawsze.
 * Przy zmianie daty księgowania kurs liczy się od nowa — inaczej przesunięcie
 * daty zostawiłoby kwotę bazową policzoną dla poprzedniej.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;
    private final MoneyConverter moneyConverter;
    private final TransactionValidator validator;

    public Page<Transaction> search(TransactionSearchCriteria criteria, Pageable pageable) {

        return transactionRepository.search(criteria.from(), criteria.to(), criteria.accountId(),
            criteria.categoryId(), criteria.type(), criteria.currency(), pageable);
    }

    public Transaction get(long id) {

        return transactionRepository.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("TRANSACTION_NOT_FOUND",
                ExceptionMessageConstants.TRANSACTION_NOT_FOUND.formatted(id)));
    }

    @Transactional
    public Transaction create(TransactionCommand command) {

        Account account = accountService.get(command.accountId());
        Account toAccount = optionalAccount(command.toAccountId());
        Category category = optionalCategory(command.categoryId());
        validator.validate(command, account, toAccount, category);

        Transaction transaction = new Transaction(command.type(), command.bookedOn(),
            command.amountMinor(), account.getCurrency(), account);
        apply(transaction, command, toAccount, category);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction update(long id, TransactionCommand command) {

        Transaction transaction = get(id);
        Account account = accountService.get(command.accountId());
        Account toAccount = optionalAccount(command.toAccountId());
        Category category = optionalCategory(command.categoryId());
        validator.validate(command, account, toAccount, category);

        transaction.rebook(command.type(), command.bookedOn(), command.amountMinor(),
            account.getCurrency(), account);
        apply(transaction, command, toAccount, category);
        return transaction;
    }

    @Transactional
    public void delete(long id) {

        transactionRepository.delete(get(id));
    }

    /** Wspólna część zapisu i zmiany: powiązania, opis i przeliczenie na walutę bazową. */
    private void apply(Transaction transaction, TransactionCommand command,
                       @Nullable Account toAccount, @Nullable Category category) {

        transaction.assignTransferTarget(toAccount, command.toAmountMinor());
        transaction.assignCategory(category);
        transaction.assignOriginal(command.originalAmountMinor(), command.originalCurrency());
        transaction.describe(command.description(), command.counterparty());

        FxRate rate = exchangeRateService.resolve(transaction.getCurrency(),
            transaction.getBookedOn());
        long baseAmountMinor = moneyConverter.convert(
            transaction.getAmountMinor(),
            currencyService.minorUnitsOf(transaction.getCurrency()),
            rate.rate(),
            currencyService.baseMinorUnits());
        transaction.applyBaseAmount(baseAmountMinor, rate);
    }

    @Nullable
    private Account optionalAccount(@Nullable Long accountId) {

        if (Objects.isNull(accountId)) {
            return null;
        }
        return accountService.get(accountId);
    }

    @Nullable
    private Category optionalCategory(@Nullable Long categoryId) {

        if (Objects.isNull(categoryId)) {
            return null;
        }
        return categoryService.get(categoryId);
    }
}
