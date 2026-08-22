package com.pgoogol.finance.recurring.infrastructure;

import com.pgoogol.finance.recurring.domain.OccurrenceStatus;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduledOccurrenceRepository extends JpaRepository<ScheduledOccurrence, Long> {

    /**
     * Terminarz z dociągniętą regułą, kontem i kategorią.
     *
     * <p>Filtr statusu przyjmuje też {@code OVERDUE}, którego w bazie nie ma:
     * to {@code PENDING} z terminem wcześniejszym niż {@code :today}. Warunek
     * siedzi w zapytaniu, bo filtrowanie po odczycie zwracałoby raz pustą, raz
     * pełną listę tej samej wielkości.</p>
     */
    @Query("""
        select o from ScheduledOccurrence o \
        join fetch o.rule r \
        join fetch r.account \
        join fetch r.category \
        where (:from is null or o.dueDate >= :from) \
          and (:to is null or o.dueDate <= :to) \
          and (:ruleId is null or r.id = :ruleId) \
          and (:status is null \
               or (:status <> com.pgoogol.finance.recurring.domain.OccurrenceStatus.OVERDUE \
                   and o.status = :status) \
               or (:status = com.pgoogol.finance.recurring.domain.OccurrenceStatus.OVERDUE \
                   and o.status = com.pgoogol.finance.recurring.domain.OccurrenceStatus.PENDING \
                   and o.dueDate < :today)) \
        order by o.dueDate asc, o.id asc""")
    List<ScheduledOccurrence> search(@Param("from") @Nullable LocalDate from,
                                     @Param("to") @Nullable LocalDate to,
                                     @Param("status") @Nullable OccurrenceStatus status,
                                     @Param("ruleId") @Nullable Long ruleId,
                                     @Param("today") LocalDate today);

    @Query("""
        select o from ScheduledOccurrence o \
        join fetch o.rule r \
        join fetch r.account \
        join fetch r.category \
        where o.id = :id""")
    Optional<ScheduledOccurrence> findDetailedById(@Param("id") long id);

    /**
     * Terminy, które reguła już ma. Generator porównuje z nimi wyliczone daty —
     * to jest jego idempotencja, obok ograniczenia {@code ux_occurrence}.
     */
    @Query("""
        select o.dueDate from ScheduledOccurrence o \
        where o.rule.id = :ruleId and o.dueDate <= :to""")
    List<LocalDate> findDueDates(@Param("ruleId") long ruleId, @Param("to") LocalDate to);

    /**
     * Usunięcie przyszłych pozycji czekających — po zmianie reguły albo po jej
     * wyłączeniu. {@code PAID} i {@code SKIPPED} zostają nietknięte: to historia.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from ScheduledOccurrence o \
        where o.rule.id = :ruleId \
          and o.status = com.pgoogol.finance.recurring.domain.OccurrenceStatus.PENDING \
          and o.dueDate >= :from""")
    int deletePendingFrom(@Param("ruleId") long ruleId, @Param("from") LocalDate from);
}
