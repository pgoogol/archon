package com.pgoogol.finance.imports.infrastructure;

import com.pgoogol.finance.imports.domain.ImportRow;
import com.pgoogol.finance.imports.domain.ImportRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImportRowRepository extends JpaRepository<ImportRow, Long> {

    /**
     * Wiersze partii do podglądu. {@code left join fetch} po obu podpowiedziach —
     * kategorii i pozycji terminarza wraz z jej regułą — bo podgląd pokazuje
     * ich nazwy przy każdym wierszu, a wyciąg miesięczny ma ich kilkaset.
     */
    @Query("""
        select r from ImportRow r
        left join fetch r.suggestedCategory
        left join fetch r.suggestedOccurrence o
        left join fetch o.rule
        where r.batch.id = :batchId
        order by r.ordinal""")
    List<ImportRow> findByBatchIdOrdered(long batchId);

    /**
     * Czy klucz jest już zajęty przez wiersz liczący się do deduplikacji.
     * Wiersze oznaczone jako duplikat świadomie klucza nie rezerwują —
     * rezerwuje go ten wpis, którego są duplikatem.
     */
    @Query("""
        select count(r) > 0 from ImportRow r
        where r.dedupKey = :dedupKey and r.status <> :duplicate""")
    boolean existsActiveByDedupKey(String dedupKey, ImportRowStatus duplicate);

    /**
     * Liczniki statusów dla wielu partii naraz. Lista wyciągów potrzebuje ich
     * dla każdej pozycji — bez tego ekran z dwunastoma wyciągami robiłby
     * dwanaście zapytań o wiersze, żeby policzyć dwie liczby.
     */
    @Query("""
        select r.batch.id as batchId, r.status as status, count(r) as total
        from ImportRow r
        where r.batch.id in :batchIds
        group by r.batch.id, r.status""")
    List<StatusCount> countByStatusForBatches(List<Long> batchIds);

    /** Projekcja licznika: ile wierszy danego statusu ma dana partia. */
    interface StatusCount {

        Long getBatchId();

        ImportRowStatus getStatus();

        long getTotal();
    }
}
