package com.pgoogol.finance.currency.infrastructure;

import com.pgoogol.finance.currency.domain.ExchangeRate;
import com.pgoogol.finance.currency.domain.ExchangeRateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, ExchangeRateId> {

    /**
     * Reguła wyboru kursu: najnowszy o dacie nie późniejszej niż podana.
     * NBP nie publikuje tabel w weekendy i święta, więc kurs "z dnia księgowania"
     * często pochodzi z piątku.
     */
    Optional<ExchangeRate> findTopByIdCodeAndIdRateDateLessThanEqualOrderByIdRateDateDesc(
        String code, LocalDate onDate);

    List<ExchangeRate> findByIdCodeAndIdRateDateBetweenOrderByIdRateDateAsc(
        String code, LocalDate from, LocalDate to);
}
