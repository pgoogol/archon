package com.pgoogol.finance.recurring.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.recurring.domain.OccurrenceStatus;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import com.pgoogol.finance.recurring.infrastructure.ScheduledOccurrenceRepository;
import com.pgoogol.finance.transaction.application.TransactionCommand;
import com.pgoogol.finance.transaction.application.TransactionService;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccurrenceServiceTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 10);

    @Mock
    private ScheduledOccurrenceRepository occurrenceRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private OccurrenceService service;

    @Test
    @DisplayName("pay gdy kwota faktyczna różni się od oczekiwanej, zapisuje faktyczną")
    void pay_whenActualAmountDiffersFromExpected_savesTheActualOne() {

        // given: rachunek za prąd rzadko wychodzi co do grosza tak samo
        ScheduledOccurrence occurrence = pendingOccurrence(10_000L);
        when(occurrenceRepository.findDetailedById(5L)).thenReturn(Optional.of(occurrence));
        Transaction transaction = new Transaction(TransactionType.EXPENSE, DUE_DATE, 11_350L,
            FinanceFixtures.PLN, occurrence.getRule().getAccount());
        when(transactionService.create(any(TransactionCommand.class))).thenReturn(transaction);

        // when
        ScheduledOccurrence paid = service.pay(5L, DUE_DATE, 11_350L, null);

        // then
        ArgumentCaptor<TransactionCommand> command =
            ArgumentCaptor.forClass(TransactionCommand.class);
        verify(transactionService).create(command.capture());
        assertAll(
            () -> assertThat(command.getValue().amountMinor()).isEqualTo(11_350L),
            () -> assertThat(command.getValue().type()).isEqualTo(TransactionType.EXPENSE),
            () -> assertThat(paid.getStatus()).isEqualTo(OccurrenceStatus.PAID),
            () -> assertThat(paid.getPaidAmountMinor()).isEqualTo(11_350L),
            () -> assertThat(paid.getExpectedAmountMinor()).isEqualTo(10_000L),
            () -> assertThat(paid.getTransaction()).isSameAs(transaction));
    }

    @Test
    @DisplayName("pay gdy pozycja jest już zapłacona, zgłasza konflikt")
    void pay_whenOccurrenceIsAlreadyPaid_reportsConflict() {

        // given
        ScheduledOccurrence occurrence = pendingOccurrence(10_000L);
        occurrence.markSkipped();
        when(occurrenceRepository.findDetailedById(5L)).thenReturn(Optional.of(occurrence));

        // when & then: druga płatność założyłaby drugą transakcję na ten sam rachunek
        assertThatThrownBy(() -> service.pay(5L, DUE_DATE, 10_000L, null))
            .isInstanceOf(ConflictException.class);
        verify(transactionService, never()).create(any());
    }

    @Test
    @DisplayName("pay gdy wskazane konto ma inną walutę, odrzuca płatność")
    void pay_whenChosenAccountHasAnotherCurrency_rejectsPayment() {

        // given
        ScheduledOccurrence occurrence = pendingOccurrence(10_000L);
        when(occurrenceRepository.findDetailedById(5L)).thenReturn(Optional.of(occurrence));
        Account foreignAccount = FinanceFixtures.account(9L, "Walutowe", FinanceFixtures.EUR);
        when(accountService.get(9L)).thenReturn(foreignAccount);

        // when & then
        assertThatThrownBy(() -> service.pay(5L, DUE_DATE, 10_000L, 9L))
            .isInstanceOf(ValidationException.class);
        verify(transactionService, never()).create(any());
    }

    @Test
    @DisplayName("skip oznacza pozycję jako pominiętą i nie tworzy transakcji")
    void skip_marksOccurrenceSkippedAndCreatesNoTransaction() {

        // given
        ScheduledOccurrence occurrence = pendingOccurrence(10_000L);
        when(occurrenceRepository.findDetailedById(5L)).thenReturn(Optional.of(occurrence));

        // when
        ScheduledOccurrence skipped = service.skip(5L);

        // then
        assertThat(skipped.getStatus()).isEqualTo(OccurrenceStatus.SKIPPED);
        verify(transactionService, never()).create(any());
    }

    @Test
    @DisplayName("get gdy pozycji nie ma, zgłasza brak")
    void get_whenOccurrenceIsMissing_reportsNotFound() {

        // given
        when(occurrenceRepository.findDetailedById(404L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
    }

    private ScheduledOccurrence pendingOccurrence(long expectedAmountMinor) {

        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        Category category = FinanceFixtures.category(2L, "Mieszkanie", CategoryDirection.EXPENSE);
        RecurringRule rule = FinanceFixtures.recurringRule(7L, account, category,
            expectedAmountMinor, LocalDate.of(2026, 1, 10));
        return FinanceFixtures.occurrence(5L, rule, DUE_DATE);
    }
}
