package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.transaction.domain.TransactionType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Kształt transakcji sprawdzany przed zapisem. Te same reguły pilnuje więz
 * {@code ck_tx_shape} w bazie — tutaj są po to, żeby użytkownik dostał
 * zrozumiały komunikat zamiast błędu bazy danych.
 */
@Component
public class TransactionValidator {

    public void validate(TransactionCommand command, Account account,
                         @Nullable Account toAccount, @Nullable Category category) {

        requireAmount(command);
        requireCurrencyOfAccount(command, account);
        requireOriginalPair(command);
        if (Objects.equals(command.type(), TransactionType.TRANSFER)) {
            requireTransferShape(command, account, toAccount, category);
        } else {
            requireFlowShape(command, toAccount, category);
        }
    }

    private void requireAmount(TransactionCommand command) {

        if (command.amountMinor() <= 0) {
            throw new ValidationException("AMOUNT_NOT_POSITIVE",
                ExceptionMessageConstants.AMOUNT_NOT_POSITIVE);
        }
    }

    private void requireCurrencyOfAccount(TransactionCommand command, Account account) {

        if (!Objects.equals(command.currency(), account.getCurrency())) {
            throw new ValidationException("CURRENCY_MISMATCH",
                ExceptionMessageConstants.CURRENCY_MISMATCH.formatted(
                    account.getName(), account.getCurrency(), command.currency()));
        }
    }

    private void requireOriginalPair(TransactionCommand command) {

        if (Objects.isNull(command.originalAmountMinor())
                != Objects.isNull(command.originalCurrency())) {
            throw new ValidationException("ORIGINAL_AMOUNT_INCOMPLETE",
                ExceptionMessageConstants.ORIGINAL_AMOUNT_INCOMPLETE);
        }
    }

    private void requireTransferShape(TransactionCommand command, Account account,
                                      @Nullable Account toAccount, @Nullable Category category) {

        if (Objects.nonNull(category)) {
            throw new ValidationException("TRANSFER_WITH_CATEGORY",
                ExceptionMessageConstants.TRANSFER_WITH_CATEGORY);
        }
        if (Objects.isNull(toAccount)) {
            throw new ValidationException("TRANSFER_WITHOUT_TARGET",
                ExceptionMessageConstants.TRANSFER_WITHOUT_TARGET);
        }
        if (Objects.equals(toAccount.getId(), account.getId())) {
            throw new ValidationException("TRANSFER_TO_SAME_ACCOUNT",
                ExceptionMessageConstants.TRANSFER_TO_SAME_ACCOUNT);
        }
        requireTargetAmount(command, account, toAccount);
    }

    /**
     * Przy różnych walutach kont kwoty obu stron nie wynikają z siebie nawzajem —
     * różnica kursowa bierze się właśnie z ich proporcji, więc kwota docelowa
     * musi być podana.
     */
    private void requireTargetAmount(TransactionCommand command, Account account,
                                     Account toAccount) {

        boolean sameCurrency = Objects.equals(account.getCurrency(), toAccount.getCurrency());
        Long toAmountMinor = command.toAmountMinor();
        if (!sameCurrency && (Objects.isNull(toAmountMinor) || toAmountMinor <= 0)) {
            throw new ValidationException("TRANSFER_TARGET_AMOUNT_REQUIRED",
                ExceptionMessageConstants.TRANSFER_TARGET_AMOUNT_REQUIRED
                    .formatted(account.getCurrency(), toAccount.getCurrency()));
        }
        if (Objects.nonNull(toAmountMinor) && toAmountMinor <= 0) {
            throw new ValidationException("AMOUNT_NOT_POSITIVE",
                ExceptionMessageConstants.TARGET_AMOUNT_NOT_POSITIVE);
        }
    }

    private void requireFlowShape(TransactionCommand command, @Nullable Account toAccount,
                                  @Nullable Category category) {

        if (Objects.nonNull(toAccount) || Objects.nonNull(command.toAmountMinor())) {
            throw new ValidationException("FLOW_WITH_TRANSFER_TARGET",
                ExceptionMessageConstants.FLOW_WITH_TRANSFER_TARGET);
        }
        if (Objects.isNull(category)) {
            throw new ValidationException("CATEGORY_REQUIRED",
                ExceptionMessageConstants.CATEGORY_REQUIRED);
        }
        requireMatchingDirection(command.type(), category);
    }

    private CategoryDirection expectedDirection(TransactionType type) {

        if (Objects.equals(type, TransactionType.INCOME)) {
            return CategoryDirection.INCOME;
        }
        return CategoryDirection.EXPENSE;
    }

    private void requireMatchingDirection(TransactionType type, Category category) {

        CategoryDirection expected = expectedDirection(type);
        if (!Objects.equals(category.getDirection(), expected)) {
            throw new ValidationException("CATEGORY_DIRECTION_MISMATCH",
                ExceptionMessageConstants.CATEGORY_DIRECTION_MISMATCH.formatted(
                    category.getName(), category.getDirection(), type));
        }
    }
}
