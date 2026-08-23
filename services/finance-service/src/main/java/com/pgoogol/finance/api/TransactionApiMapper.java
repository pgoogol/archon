package com.pgoogol.finance.api;

import com.pgoogol.finance.transaction.application.TransactionCommand;
import com.pgoogol.finance.transaction.domain.Transaction;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = DecimalMapper.class)
public interface TransactionApiMapper {

    /**
     * Waluta bazowa przychodzi drugim parametrem zamiast wstrzykniętej zależności —
     * mapper zostaje bezstanowy i nie sięga po konfigurację.
     */
    @Mapping(target = "accountId", source = "transaction.account.id")
    @Mapping(target = "accountName", source = "transaction.account.name")
    @Mapping(target = "toAccountId", source = "transaction.toAccount.id")
    @Mapping(target = "toAccountName", source = "transaction.toAccount.name")
    @Mapping(target = "categoryId", source = "transaction.category.id")
    @Mapping(target = "categoryName", source = "transaction.category.name")
    @Mapping(target = "baseCurrency", source = "baseCurrency")
    TransactionResponse toResponse(Transaction transaction, String baseCurrency);

    TransactionCommand toCommand(TransactionRequest request);
}
