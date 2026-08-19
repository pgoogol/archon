package com.pgoogol.finance.account.application;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountType;
import com.pgoogol.finance.account.infrastructure.AccountRepository;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.currency.application.CurrencyService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrencyService currencyService;

    public AccountService(AccountRepository accountRepository, CurrencyService currencyService) {

        this.accountRepository = accountRepository;
        this.currencyService = currencyService;
    }

    public List<Account> list(boolean includeArchived) {

        return includeArchived
            ? accountRepository.findAllByOrderByNameAsc()
            : accountRepository.findByArchivedFalseOrderByNameAsc();
    }

    public Account get(long id) {

        return accountRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                "Konto %d nie istnieje".formatted(id)));
    }

    @Transactional
    public Account create(String name, AccountType type, String currency, @Nullable String iban,
                          long openingBalanceMinor, LocalDate openingBalanceOn) {

        // waluta musi być w słowniku — inaczej nie znamy jej skali i każda kwota
        // na tym koncie byłaby zapisana z przypadkową liczbą miejsc po przecinku
        String code = currencyService.get(currency).getCode();
        return accountRepository.save(new Account(name, type, code, iban,
            openingBalanceMinor, openingBalanceOn));
    }

    @Transactional
    public Account update(long id, String name, AccountType type, @Nullable String iban,
                          long openingBalanceMinor, LocalDate openingBalanceOn) {

        Account account = get(id);
        account.rename(name, type, iban, openingBalanceMinor, openingBalanceOn);
        return account;
    }

    @Transactional
    public void archive(long id) {
        get(id).archive();
    }
}
