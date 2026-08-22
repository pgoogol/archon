package com.pgoogol.finance.transaction.infrastructure;

import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Lista transakcji z dociągniętymi kontami i kategorią. Bez {@code join fetch}
     * każdy wiersz odpytywałby bazę o nazwę konta i kategorii z osobna.
     *
     * <p>Filtr konta obejmuje też stronę docelową transferu — przelew ma pojawić
     * się w historii obu kont, nie tylko źródłowego.</p>
     */
    @Query(value = """
        select t from Transaction t \
        join fetch t.account a \
        left join fetch t.toAccount ta \
        left join fetch t.category c \
        where (:from is null or t.bookedOn >= :from) \
          and (:to is null or t.bookedOn <= :to) \
          and (:accountId is null or a.id = :accountId or ta.id = :accountId) \
          and (:categoryId is null or c.id = :categoryId) \
          and (:type is null or t.type = :type) \
          and (:currency is null or t.currency = :currency) \
        order by t.bookedOn desc, t.id desc""",
        countQuery = """
        select count(t) from Transaction t \
        where (:from is null or t.bookedOn >= :from) \
          and (:to is null or t.bookedOn <= :to) \
          and (:accountId is null or t.account.id = :accountId \
               or t.toAccount.id = :accountId) \
          and (:categoryId is null or t.category.id = :categoryId) \
          and (:type is null or t.type = :type) \
          and (:currency is null or t.currency = :currency)""")
    Page<Transaction> search(@Param("from") @Nullable LocalDate from,
                             @Param("to") @Nullable LocalDate to,
                             @Param("accountId") @Nullable Long accountId,
                             @Param("categoryId") @Nullable Long categoryId,
                             @Param("type") @Nullable TransactionType type,
                             @Param("currency") @Nullable String currency,
                             Pageable pageable);

    @Query("""
        select t from Transaction t \
        join fetch t.account \
        left join fetch t.toAccount \
        left join fetch t.category \
        where t.id = :id""")
    Optional<Transaction> findDetailedById(@Param("id") long id);

    /**
     * Powiązania wierszy wyciągu z transakcjami, które z nich powstały.
     * Jedno zapytanie na całą partię zamiast jednego na wiersz — wyciąg
     * miesięczny ma ich kilkaset.
     */
    @Query("""
        select t.importRow.id as importRowId, t.id as transactionId \
        from Transaction t \
        where t.importRow.id in :rowIds""")
    List<ImportedTransactionRef> findRefsByImportRowIds(@Param("rowIds") Collection<Long> rowIds);

    /** Projekcja: który wiersz wyciągu stał się którą transakcją. */
    interface ImportedTransactionRef {

        Long getImportRowId();

        Long getTransactionId();
    }
}
