package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.transaction.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kształt transakcji. Te same reguły ma więz {@code ck_tx_shape} w bazie —
 * tutaj sprawdzamy, że użytkownik dostaje zrozumiały komunikat, zanim zapytanie
 * w ogóle poleci do Postgresa.
 */
class TransactionValidatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 19);

    private final TransactionValidator validator = new TransactionValidator();

    private final Account zlotyAccount = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
    private final Account euroAccount = FinanceFixtures.account(2L, "Walutowe", FinanceFixtures.EUR);
    private final Category expenseCategory =
        FinanceFixtures.category(10L, "Jedzenie", CategoryDirection.EXPENSE);
    private final Category incomeCategory =
        FinanceFixtures.category(11L, "Wynagrodzenie", CategoryDirection.INCOME);

    @Test
    @DisplayName("transfer z kategorią jest odrzucany — nie jest wydatkiem ani przychodem")
    void validate_whenTransferHasCategory_fails() {

        // given
        TransactionCommand command = new TransactionCommand(TransactionType.TRANSFER, DAY, 10_000L,
            FinanceFixtures.PLN, null, null, 1L, 2L, 2_300L, 10L, null, null);

        // when / then
        assertThatThrownBy(() ->
            validator.validate(command, zlotyAccount, euroAccount, expenseCategory))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Transfer nie ma kategorii");
    }

    @Test
    @DisplayName("wydatek bez kategorii jest odrzucany")
    void validate_whenExpenseHasNoCategory_fails() {

        // given
        TransactionCommand command = expense(null);

        // when / then
        assertThatThrownBy(() -> validator.validate(command, zlotyAccount, null, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("wymagają kategorii");
    }

    @Test
    @DisplayName("transfer na to samo konto niczego nie zmienia i jest odrzucany")
    void validate_whenTransferTargetsSourceAccount_fails() {

        // given
        TransactionCommand command = transfer(1L, null);

        // when / then
        assertThatThrownBy(() -> validator.validate(command, zlotyAccount, zlotyAccount, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("to samo konto");
    }

    @Test
    @DisplayName("transfer między różnymi walutami wymaga kwoty po stronie docelowej")
    void validate_whenTransferCrossesCurrencies_requiresTargetAmount() {

        // given
        TransactionCommand command = transfer(2L, null);

        // when / then
        assertThatThrownBy(() -> validator.validate(command, zlotyAccount, euroAccount, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("kwotę po stronie docelowej");
    }

    @Test
    @DisplayName("transfer między różnymi walutami z podaną kwotą docelową przechodzi")
    void validate_whenTransferCrossesCurrenciesWithTargetAmount_passes() {

        // given
        TransactionCommand command = transfer(2L, 2_300L);

        // when / then
        assertThatCode(() -> validator.validate(command, zlotyAccount, euroAccount, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("kwota w walucie innej niż waluta konta jest odrzucana")
    void validate_whenCurrencyDiffersFromAccount_fails() {

        // given
        TransactionCommand command = new TransactionCommand(TransactionType.EXPENSE, DAY, 1_000L,
            FinanceFixtures.EUR, null, null, 1L, null, null, 10L, null, null);

        // when / then
        assertThatThrownBy(() ->
            validator.validate(command, zlotyAccount, null, expenseCategory))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("w walucie konta");
    }

    @Test
    @DisplayName("kategoria przychodowa na wydatku jest odrzucana")
    void validate_whenCategoryDirectionMismatches_fails() {

        // given
        TransactionCommand command = expense(11L);

        // when / then
        assertThatThrownBy(() ->
            validator.validate(command, zlotyAccount, null, incomeCategory))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("kategorią INCOME");
    }

    @Test
    @DisplayName("kwota oryginalna bez waluty oryginalnej jest odrzucana")
    void validate_whenOriginalAmountHasNoCurrency_fails() {

        // given
        TransactionCommand command = new TransactionCommand(TransactionType.EXPENSE, DAY, 1_000L,
            FinanceFixtures.PLN, 250L, null, 1L, null, null, 10L, null, null);

        // when / then
        assertThatThrownBy(() ->
            validator.validate(command, zlotyAccount, null, expenseCategory))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("razem albo wcale");
    }

    @Test
    @DisplayName("wydatek z kontem docelowym jest odrzucany — to byłby transfer")
    void validate_whenExpenseHasTransferTarget_fails() {

        // given
        TransactionCommand command = new TransactionCommand(TransactionType.EXPENSE, DAY, 1_000L,
            FinanceFixtures.PLN, null, null, 1L, 2L, null, 10L, null, null);

        // when / then
        assertThatThrownBy(() ->
            validator.validate(command, zlotyAccount, euroAccount, expenseCategory))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("użyj typu TRANSFER");
    }

    @Test
    @DisplayName("poprawny wydatek przechodzi walidację")
    void validate_whenExpenseIsWellFormed_passes() {

        assertThatCode(() ->
            validator.validate(expense(10L), zlotyAccount, null, expenseCategory))
            .doesNotThrowAnyException();
    }

    private TransactionCommand expense(Long categoryId) {

        return new TransactionCommand(TransactionType.EXPENSE, DAY, 1_000L, FinanceFixtures.PLN,
            null, null, 1L, null, null, categoryId, null, null);
    }

    private TransactionCommand transfer(long toAccountId, Long toAmountMinor) {

        return new TransactionCommand(TransactionType.TRANSFER, DAY, 10_000L, FinanceFixtures.PLN,
            null, null, 1L, toAccountId, toAmountMinor, null, null, null);
    }
}
