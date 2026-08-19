package com.pgoogol.finance.account.infrastructure;

import com.pgoogol.finance.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByOrderByNameAsc();

    List<Account> findByArchivedFalseOrderByNameAsc();

    /**
     * Saldo liczone w bazie jednym zapytaniem, nigdy pętlą po transakcjach.
     *
     * <p>Wydatek i wychodzący transfer zmniejszają saldo, przychód je zwiększa,
     * a transfer przychodzący dokłada kwotę po stronie konta docelowego —
     * przy różnych walutach kont jest to {@code to_amount_minor}. Transakcje
     * sprzed daty salda otwarcia są pomijane, bo saldo otwarcia już je zawiera.</p>
     */
    @Query(value = """
        select a.opening_balance_minor \
        + coalesce((select sum(case t.type when 'INCOME' then t.amount_minor \
                                           else -t.amount_minor end) \
                    from {h-schema}transaction t \
                    where t.account_id = a.id \
                      and t.booked_on >= a.opening_balance_on), 0) \
        + coalesce((select sum(coalesce(t.to_amount_minor, t.amount_minor)) \
                    from {h-schema}transaction t \
                    where t.to_account_id = a.id \
                      and t.type = 'TRANSFER' \
                      and t.booked_on >= a.opening_balance_on), 0) \
        from {h-schema}account a \
        where a.id = :accountId""", nativeQuery = true)
    Optional<Long> findBalanceMinor(@Param("accountId") long accountId);
}
