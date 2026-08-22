package com.pgoogol.finance.api;

import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountBalance;
import com.pgoogol.finance.account.application.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/accounts")
@Tag(name = "accounts", description = "Konta i salda")
public class AccountController {

    private final AccountService accountService;
    private final BalanceService balanceService;
    private final AccountApiMapper mapper;

    public AccountController(AccountService accountService, BalanceService balanceService,
                             AccountApiMapper mapper) {

        this.accountService = accountService;
        this.balanceService = balanceService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Lista kont")
    public List<AccountResponse> listAccounts(
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<Account> accounts = accountService.list(includeArchived);
        return mapper.toResponses(accounts);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowe konto")
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {

        Account account = accountService.create(request.name(), request.type(),
            request.currency(), request.iban(), request.openingBalanceMinor(),
            request.openingBalanceOn());
        return mapper.toResponse(account);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmiana danych konta",
        description = """
            Waluty konta nie da się zmienić — kwoty istniejących transakcji \
            są zapisane w niej i zmiana rozjechałaby saldo.""")
    public AccountResponse updateAccount(@PathVariable long id,
                                         @Valid @RequestBody AccountRequest request) {

        Account account = accountService.update(id, request.name(), request.type(),
            request.iban(), request.openingBalanceMinor(), request.openingBalanceOn());
        return mapper.toResponse(account);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archiwizacja konta",
        description = "Konto nie znika — historia transakcji musi zostać.")
    public void archiveAccount(@PathVariable long id) {

        accountService.archive(id);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Saldo konta w jego walucie i w walucie bazowej",
        description = """
            Saldo liczy się zapytaniem, nie jest przechowywane. Wartość bazowa \
            przelicza się kursem bieżącym — to stan majątku na dziś, w odróżnieniu \
            od transakcji, które trzymają kurs historyczny.""")
    public AccountBalanceResponse getAccountBalance(@PathVariable long id) {

        AccountBalance balance = balanceService.balanceOf(id);
        return mapper.toResponse(balance);
    }
}
