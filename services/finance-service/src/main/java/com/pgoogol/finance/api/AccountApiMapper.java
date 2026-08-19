package com.pgoogol.finance.api;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.domain.AccountBalance;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = DecimalMapper.class)
public interface AccountApiMapper {

    AccountResponse toResponse(Account account);

    List<AccountResponse> toResponses(List<Account> accounts);

    AccountBalanceResponse toResponse(AccountBalance balance);
}
