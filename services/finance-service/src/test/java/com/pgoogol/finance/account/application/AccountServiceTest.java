package com.pgoogol.finance.account.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountType;
import com.pgoogol.finance.account.infrastructure.AccountRepository;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.currency.application.CurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CurrencyService currencyService;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("domyślna lista pomija konta zarchiwizowane")
    void list_whenArchivedNotRequested_returnsOnlyActive() {

        // given
        when(accountRepository.findByArchivedFalseOrderByNameAsc()).thenReturn(List.of());

        // when
        accountService.list(false);

        // then
        verify(accountRepository).findByArchivedFalseOrderByNameAsc();
    }

    @Test
    @DisplayName("konto zakłada się tylko w walucie ze słownika")
    void create_whenCurrencyUnknown_fails() {

        // given
        when(currencyService.get("XYZ"))
            .thenThrow(new NotFoundException("CURRENCY_NOT_FOUND", "brak"));

        // when / then
        assertThatThrownBy(() -> accountService.create("Konto", AccountType.BANK, "XYZ", null,
            0L, LocalDate.of(2026, 1, 1)))
            .isInstanceOf(NotFoundException.class);
        verify(accountRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("kod waluty zapisuje się w postaci znormalizowanej ze słownika")
    void create_whenCurrencyLowercase_storesNormalizedCode() {

        // given
        when(currencyService.get("eur")).thenReturn(FinanceFixtures.euro());
        when(accountRepository.save(any(Account.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        accountService.create("Walutowe", AccountType.BANK, "eur", null, 0L,
            LocalDate.of(2026, 1, 1));

        // then
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getCurrency()).isEqualTo(FinanceFixtures.EUR);
    }

    @Test
    @DisplayName("usunięcie konta jest archiwizacją — wiersz zostaje w bazie")
    void archive_whenAccountExists_marksArchivedInsteadOfDeleting() {

        // given
        Account account = FinanceFixtures.account(1L, "Bieżące", FinanceFixtures.PLN);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // when
        accountService.archive(1L);

        // then
        assertThat(account.isArchived()).isTrue();
        verify(accountRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("lista z archiwalnymi sięga po komplet kont")
    void list_whenArchivedRequested_returnsAllAccounts() {

        // given
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        // when
        accountService.list(true);

        // then
        verify(accountRepository).findAllByOrderByNameAsc();
        verify(accountRepository, org.mockito.Mockito.never()).findByArchivedFalseOrderByNameAsc();
    }

    @Test
    @DisplayName("edycja konta nie rusza jego waluty — kwoty transakcji są w niej zapisane")
    void update_whenAccountEdited_keepsCurrency() {

        // given
        Account account = FinanceFixtures.account(4L, "Stara nazwa", FinanceFixtures.EUR);
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account));

        // when
        Account updated = accountService.update(4L, "Nowa nazwa", AccountType.CASH, "PL61",
            -5_000L, LocalDate.of(2026, 2, 1));

        // then
        assertThat(updated.getName()).isEqualTo("Nowa nazwa");
        assertThat(updated.getType()).isEqualTo(AccountType.CASH);
        assertThat(updated.getIban()).isEqualTo("PL61");
        assertThat(updated.getOpeningBalanceMinor()).isEqualTo(-5_000L);
        assertThat(updated.getCurrency()).isEqualTo(FinanceFixtures.EUR);
    }

    @Test
    @DisplayName("nieistniejące konto kończy się błędem 404, nie pustym Optionalem")
    void get_whenAccountMissing_fails() {

        // given
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> accountService.get(99L))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("99");
    }
}
