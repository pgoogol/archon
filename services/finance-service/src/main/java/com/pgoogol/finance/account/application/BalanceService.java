package com.pgoogol.finance.account.application;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountBalance;
import com.pgoogol.finance.account.infrastructure.AccountRepository;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.application.ExchangeRateService;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.currency.domain.MoneyConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saldo konta. Liczone zapytaniem przy każdym pytaniu, nigdy przechowywane —
 * przechowywane saldo rozjeżdża się z historią przy pierwszej korekcie transakcji.
 */
@Service
@Transactional(readOnly = true)
public class BalanceService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;
    private final MoneyConverter moneyConverter;

    public BalanceService(AccountRepository accountRepository,
                          AccountService accountService,
                          CurrencyService currencyService,
                          ExchangeRateService exchangeRateService,
                          MoneyConverter moneyConverter) {

        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.currencyService = currencyService;
        this.exchangeRateService = exchangeRateService;
        this.moneyConverter = moneyConverter;
    }

    public AccountBalance balanceOf(long accountId) {

        Account account = accountService.get(accountId);
        long balanceMinor = accountRepository.findBalanceMinor(accountId)
            .orElse(account.getOpeningBalanceMinor());
        MinorUnits accountUnits = currencyService.minorUnitsOf(account.getCurrency());
        MinorUnits baseUnits = currencyService.baseMinorUnits();
        // majątek wycenia się na dziś, więc kurs bieżący; kurs historyczny należy
        // do transakcji, nie do salda
        FxRate rate = exchangeRateService.current(account.getCurrency());
        long baseBalanceMinor =
            moneyConverter.convert(balanceMinor, accountUnits, rate.rate(), baseUnits);
        return new AccountBalance(
            accountId,
            account.getCurrency(),
            accountUnits.scale(),
            balanceMinor,
            currencyService.baseCurrency(),
            baseBalanceMinor,
            rate.rate(),
            rate.rateDate());
    }
}
