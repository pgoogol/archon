package com.pgoogol.finance.recurring.infrastructure;

import com.pgoogol.finance.recurring.domain.RecurringRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, Long> {

    /**
     * Reguły z dociągniętym kontem i kategorią — odpowiedź API pokazuje ich
     * nazwy, więc bez {@code join fetch} lista kilkunastu reguł to kilkadziesiąt
     * zapytań.
     */
    @Query("""
        select r from RecurringRule r \
        join fetch r.account \
        join fetch r.category \
        where (:activeOnly = false or r.active = true) \
        order by r.name asc, r.id asc""")
    List<RecurringRule> findAllDetailed(@Param("activeOnly") boolean activeOnly);

    @Query("""
        select r from RecurringRule r \
        join fetch r.account \
        join fetch r.category \
        where r.id = :id""")
    Optional<RecurringRule> findDetailedById(@Param("id") long id);

    /** Wsad dla generatora: tylko to, co ma jeszcze cokolwiek wygenerować. */
    @Query("""
        select r from RecurringRule r \
        where r.active = true \
        order by r.id asc""")
    List<RecurringRule> findActive();
}
