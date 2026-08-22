package com.pgoogol.finance.recurring.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.recurring.domain.RecurringFrequency;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.infrastructure.RecurringRuleRepository;
import com.pgoogol.finance.recurring.infrastructure.ScheduledOccurrenceRepository;
import com.pgoogol.finance.transaction.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringRuleServiceTest {

    @Mock
    private RecurringRuleRepository recurringRuleRepository;

    @Mock
    private ScheduledOccurrenceRepository occurrenceRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private OccurrenceGenerator occurrenceGenerator;

    @InjectMocks
    private RecurringRuleService service;

    @Test
    @DisplayName("create gdy dane są poprawne, zapisuje regułę w walucie konta")
    void create_whenCommandIsValid_savesRuleInAccountCurrency() {

        // given
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.EUR);
        Category category = FinanceFixtures.category(2L, "Mieszkanie", CategoryDirection.EXPENSE);
        when(accountService.get(1L)).thenReturn(account);
        when(categoryService.get(2L)).thenReturn(category);
        when(recurringRuleRepository.save(any(RecurringRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        RecurringRule created = service.create(command(TransactionType.EXPENSE, 12_000L, null));

        // then: waluta bierze się z konta, nie z żądania
        assertThat(created.getCurrency()).isEqualTo(FinanceFixtures.EUR);
        assertThat(created.isActive()).isTrue();
        verify(occurrenceGenerator).generateFor(created);
    }

    @Test
    @DisplayName("create gdy typ to TRANSFER, odrzuca regułę")
    void create_whenTypeIsTransfer_rejectsRule() {

        // given: transfer nie jest ani wydatkiem, ani przychodem — rachunek
        // cykliczny opisuje jedno albo drugie
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        Category category = FinanceFixtures.category(2L, "Mieszkanie", CategoryDirection.EXPENSE);
        when(accountService.get(1L)).thenReturn(account);
        when(categoryService.get(2L)).thenReturn(category);

        // when & then
        assertThatThrownBy(() -> service.create(command(TransactionType.TRANSFER, 12_000L, null)))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("TRANSFER");
        verify(recurringRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("create gdy kierunek kategorii nie pasuje do typu, odrzuca regułę")
    void create_whenCategoryDirectionMismatches_rejectsRule() {

        // given
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        Category category = FinanceFixtures.category(2L, "Wypłata", CategoryDirection.INCOME);
        when(accountService.get(1L)).thenReturn(account);
        when(categoryService.get(2L)).thenReturn(category);

        // when & then
        assertThatThrownBy(() -> service.create(command(TransactionType.EXPENSE, 12_000L, null)))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Wypłata");
    }

    @Test
    @DisplayName("create gdy kwota nie jest dodatnia, odrzuca regułę")
    void create_whenAmountIsNotPositive_rejectsRule() {

        // given
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        Category category = FinanceFixtures.category(2L, "Mieszkanie", CategoryDirection.EXPENSE);
        when(accountService.get(1L)).thenReturn(account);
        when(categoryService.get(2L)).thenReturn(category);

        // when & then
        assertThatThrownBy(() -> service.create(command(TransactionType.EXPENSE, 0L, null)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("update przelicza terminarz od dzisiaj i zostawia historię")
    void update_recalculatesScheduleFromToday() {

        // given
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        Category category = FinanceFixtures.category(2L, "Mieszkanie", CategoryDirection.EXPENSE);
        RecurringRule rule = FinanceFixtures.recurringRule(7L, account, category, 10_000L,
            LocalDate.of(2026, 1, 10));
        when(recurringRuleRepository.findDetailedById(7L)).thenReturn(Optional.of(rule));
        when(accountService.get(1L)).thenReturn(account);
        when(categoryService.get(2L)).thenReturn(category);

        // when
        RecurringRule updated = service.update(7L, command(TransactionType.EXPENSE, 15_000L, null));

        // then: kasujemy wyłącznie czekające od dziś, resztę zostawiamy
        ArgumentCaptor<LocalDate> odKiedy = ArgumentCaptor.forClass(LocalDate.class);
        verify(occurrenceRepository).deletePendingFrom(anyLong(), odKiedy.capture());
        assertThat(odKiedy.getValue()).isEqualTo(LocalDate.now());
        assertThat(updated.getAmountMinor()).isEqualTo(15_000L);
        verify(occurrenceGenerator).generateFor(rule);
    }

    @Test
    @DisplayName("deactivate wyłącza regułę i usuwa przyszłe pozycje czekające")
    void deactivate_disablesRuleAndDropsFuturePending() {

        // given
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        Category category = FinanceFixtures.category(2L, "Mieszkanie", CategoryDirection.EXPENSE);
        RecurringRule rule = FinanceFixtures.recurringRule(7L, account, category, 10_000L,
            LocalDate.of(2026, 1, 10));
        when(recurringRuleRepository.findDetailedById(7L)).thenReturn(Optional.of(rule));

        // when
        service.deactivate(7L);

        // then
        assertThat(rule.isActive()).isFalse();
        verify(occurrenceRepository).deletePendingFrom(7L, LocalDate.now());
        verify(occurrenceGenerator, never()).generateFor(any());
    }

    @Test
    @DisplayName("get gdy reguły nie ma, zgłasza brak")
    void get_whenRuleIsMissing_reportsNotFound() {

        // given
        when(recurringRuleRepository.findDetailedById(404L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
    }

    private RecurringRuleCommand command(TransactionType type, long amountMinor,
                                         @Nullable LocalDate endsOn) {

        return new RecurringRuleCommand("Prąd", 1L, 2L, type, amountMinor,
            RecurringFrequency.MONTHLY, 10, LocalDate.of(2026, 1, 10), endsOn, null);
    }
}
