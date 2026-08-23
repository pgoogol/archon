package com.pgoogol.finance.account.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountBalance;
import com.pgoogol.finance.account.infrastructure.AccountRepository;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.application.ExchangeRateService;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.currency.domain.MoneyConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private ExchangeRateService exchangeRateService;

    private BalanceService balanceService() {

        return new BalanceService(accountRepository, accountService, currencyService,
            exchangeRateService, new MoneyConverter());
    }

    @Test
    @DisplayName("saldo konta walutowego wycenia się kursem bieżącym, nie historycznym")
    void balanceOf_whenAccountIsForeign_convertsWithCurrentRate() {

        // given
        Account account = FinanceFixtures.account(7L, "Oszczędnościowe", FinanceFixtures.EUR);
        LocalDate today = LocalDate.of(2026, 8, 19);
        when(accountService.get(7L)).thenReturn(account);
        when(accountRepository.findBalanceMinor(7L)).thenReturn(Optional.of(10_000L));
        when(currencyService.minorUnitsOf(FinanceFixtures.EUR)).thenReturn(new MinorUnits(2));
        when(currencyService.baseMinorUnits()).thenReturn(new MinorUnits(2));
        when(currencyService.baseCurrency()).thenReturn(FinanceFixtures.PLN);
        when(exchangeRateService.current(FinanceFixtures.EUR))
            .thenReturn(new FxRate(new BigDecimal("4.3215"), today));

        // when
        AccountBalance balance = balanceService().balanceOf(7L);

        // then
        assertThat(balance.balanceMinor()).isEqualTo(10_000L);
        assertThat(balance.baseBalanceMinor()).isEqualTo(43_215L);
        assertThat(balance.baseCurrency()).isEqualTo(FinanceFixtures.PLN);
        assertThat(balance.fxRateDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("konto bez transakcji ma saldo równe saldu otwarcia")
    void balanceOf_whenNoTransactions_fallsBackToOpeningBalance() {

        // given
        Account account = FinanceFixtures.account(3L, "Gotówka", FinanceFixtures.PLN);
        when(accountService.get(3L)).thenReturn(account);
        when(accountRepository.findBalanceMinor(3L)).thenReturn(Optional.empty());
        when(currencyService.minorUnitsOf(FinanceFixtures.PLN)).thenReturn(new MinorUnits(2));
        when(currencyService.baseMinorUnits()).thenReturn(new MinorUnits(2));
        when(currencyService.baseCurrency()).thenReturn(FinanceFixtures.PLN);
        when(exchangeRateService.current(FinanceFixtures.PLN))
            .thenReturn(FxRate.identity(LocalDate.of(2026, 8, 19)));

        // when
        AccountBalance balance = balanceService().balanceOf(3L);

        // then
        assertThat(balance.balanceMinor()).isEqualTo(account.getOpeningBalanceMinor());
        assertThat(balance.baseBalanceMinor()).isEqualTo(account.getOpeningBalanceMinor());
    }
}
