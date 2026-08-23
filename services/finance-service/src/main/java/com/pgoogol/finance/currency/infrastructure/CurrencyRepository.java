package com.pgoogol.finance.currency.infrastructure;

import com.pgoogol.finance.currency.domain.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, String> {

    List<Currency> findAllByOrderByCodeAsc();
}
