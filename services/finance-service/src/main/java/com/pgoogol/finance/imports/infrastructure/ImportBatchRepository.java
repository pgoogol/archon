package com.pgoogol.finance.imports.infrastructure;

import com.pgoogol.finance.imports.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    /** Ten sam plik na tym samym koncie — podstawa odrzucenia przed parsowaniem. */
    Optional<ImportBatch> findByAccountIdAndFileHash(long accountId, String fileHash);

    @Query("""
        select b from ImportBatch b
        join fetch b.account
        where (:accountId is null or b.account.id = :accountId)
        order by b.uploadedAt desc""")
    List<ImportBatch> findAllForAccount(Long accountId);

    @Query("""
        select b from ImportBatch b
        join fetch b.account
        where b.id = :id""")
    Optional<ImportBatch> findDetailedById(long id);
}
