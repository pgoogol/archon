package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import com.pgoogol.finance.transaction.infrastructure.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferSuggestionServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    private final Account currentAccount = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
    private final Account savingsAccount =
        FinanceFixtures.account(2L, "Oszczędnościowe", FinanceFixtures.PLN);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransferSuggestionService service;

    @Test
    @DisplayName("candidates gdy kwoty są równe i odstęp to dwa dni, proponuje scalenie")
    void candidates_whenAmountsMatchWithinThreeDays_proposesMerge() {

        // given
        Transaction expense = transaction(10L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction income =
            transaction(11L, TransactionType.INCOME, savingsAccount, DAY.plusDays(2), 50_000L);
        when(transactionRepository.findFlowsBetween(FROM, TO)).thenReturn(List.of(expense, income));

        // when
        List<TransferSuggestionService.TransferCandidate> candidates = service.candidates(FROM, TO);

        // then
        assertThat(candidates).hasSize(1);
        assertAll(
            () -> assertThat(candidates.getFirst().expenseTransactionId()).isEqualTo(10L),
            () -> assertThat(candidates.getFirst().incomeTransactionId()).isEqualTo(11L),
            () -> assertThat(candidates.getFirst().daysApart()).isEqualTo(2L));
    }

    @Test
    @DisplayName("candidates gdy odstęp to cztery dni, nie proponuje niczego")
    void candidates_whenFourDaysApart_proposesNothing() {

        // given
        Transaction expense = transaction(10L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction income =
            transaction(11L, TransactionType.INCOME, savingsAccount, DAY.plusDays(4), 50_000L);
        when(transactionRepository.findFlowsBetween(FROM, TO)).thenReturn(List.of(expense, income));

        // when
        List<TransferSuggestionService.TransferCandidate> candidates = service.candidates(FROM, TO);

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("candidates gdy obie strony są na tym samym koncie, nie proponuje niczego")
    void candidates_whenBothSidesAreOnOneAccount_proposesNothing() {

        // given: zwrot za zakup na tym samym koncie nie jest przelewem
        Transaction expense = transaction(10L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction income = transaction(11L, TransactionType.INCOME, currentAccount, DAY, 50_000L);
        when(transactionRepository.findFlowsBetween(FROM, TO)).thenReturn(List.of(expense, income));

        // when
        List<TransferSuggestionService.TransferCandidate> candidates = service.candidates(FROM, TO);

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("candidates gdy jeden wpływ pasuje do dwóch wydatków, zużywa go raz")
    void candidates_whenOneIncomeFitsTwoExpenses_usesItOnce() {

        // given
        Transaction firstExpense = transaction(10L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction secondExpense = transaction(12L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction income =
            transaction(11L, TransactionType.INCOME, savingsAccount, DAY, 50_000L);
        when(transactionRepository.findFlowsBetween(FROM, TO))
            .thenReturn(List.of(firstExpense, secondExpense, income));

        // when
        List<TransferSuggestionService.TransferCandidate> candidates = service.candidates(FROM, TO);

        // then: druga strona przelewu jest jedna, więc para też jest jedna
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().expenseTransactionId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("merge zakłada przelew i usuwa obie strony")
    void merge_createsTransferAndRemovesBothSides() {

        // given
        Transaction expense = transaction(10L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction income =
            transaction(11L, TransactionType.INCOME, savingsAccount, DAY.plusDays(1), 50_000L);
        when(transactionService.get(10L)).thenReturn(expense);
        when(transactionService.get(11L)).thenReturn(income);
        when(transactionService.create(any(TransactionCommand.class)))
            .thenReturn(transaction(12L, TransactionType.TRANSFER, currentAccount, DAY, 50_000L));

        // when
        service.merge(10L, 11L);

        // then
        ArgumentCaptor<TransactionCommand> command =
            ArgumentCaptor.forClass(TransactionCommand.class);
        verify(transactionService).create(command.capture());
        assertAll(
            () -> assertThat(command.getValue().type()).isEqualTo(TransactionType.TRANSFER),
            () -> assertThat(command.getValue().accountId()).isEqualTo(1L),
            () -> assertThat(command.getValue().toAccountId()).isEqualTo(2L),
            // ta sama waluta po obu stronach — kwota docelowa byłaby powtórzeniem
            () -> assertThat(command.getValue().toAmountMinor()).isNull());
        verify(transactionRepository).delete(expense);
        verify(transactionRepository).delete(income);
    }

    @Test
    @DisplayName("merge gdy para przestała pasować, odrzuca scalenie")
    void merge_whenPairNoLongerMatches_rejectsIt() {

        // given: między podglądem a potwierdzeniem ktoś zmienił kwotę
        Transaction expense = transaction(10L, TransactionType.EXPENSE, currentAccount, DAY, 50_000L);
        Transaction income =
            transaction(11L, TransactionType.INCOME, savingsAccount, DAY, 40_000L);
        when(transactionService.get(10L)).thenReturn(expense);
        when(transactionService.get(11L)).thenReturn(income);

        // when & then
        assertThatThrownBy(() -> service.merge(10L, 11L))
            .isInstanceOf(ValidationException.class);
        verify(transactionService, never()).create(any());
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    private Transaction transaction(long id, TransactionType type, Account account,
                                    LocalDate bookedOn, long amountMinor) {

        Transaction transaction =
            new Transaction(type, bookedOn, amountMinor, account.getCurrency(), account);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }
}
