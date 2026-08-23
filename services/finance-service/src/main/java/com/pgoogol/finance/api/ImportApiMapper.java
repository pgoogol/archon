package com.pgoogol.finance.api;

import com.pgoogol.finance.imports.application.ImportService;
import com.pgoogol.finance.imports.application.ReconciliationService;
import com.pgoogol.finance.imports.domain.ImportBatch;
import com.pgoogol.finance.imports.domain.ImportRow;
import com.pgoogol.finance.imports.domain.ImportRowStatus;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ImportApiMapper {

    /**
     * Liczniki wierszy przychodzą parametrami, a nie z encji — partia ich nie
     * przechowuje, bo wyliczone raz przy wgraniu rozjechałyby się z prawdą po
     * pierwszym zatwierdzeniu.
     */
    @Mapping(target = "accountId", source = "batch.account.id")
    @Mapping(target = "accountName", source = "batch.account.name")
    @Mapping(target = "newCount", source = "newCount")
    @Mapping(target = "duplicateCount", source = "duplicateCount")
    ImportBatchResponse toResponse(ImportBatch batch, int newCount, int duplicateCount);

    @Mapping(target = "suggestedCategoryId", source = "row.suggestedCategory.id")
    @Mapping(target = "suggestedCategoryName", source = "row.suggestedCategory.name")
    @Mapping(target = "suggestedOccurrenceId", source = "row.suggestedOccurrence.id")
    @Mapping(target = "suggestedOccurrenceName", source = "row.suggestedOccurrence.rule.name")
    @Mapping(target = "transactionId", source = "transactionId")
    ImportRowResponse toResponse(ImportRow row, Long transactionId);

    ReconciliationResponse toResponse(ReconciliationService.Reconciliation reconciliation);

    @Mapping(target = "reconciliation", source = "reconciliation")
    CommitImportResponse toResponse(ImportService.CommitResult result);

    /** Partia bez wierszy — liczniki i tak trzeba znać, więc idą razem z nimi. */
    default ImportBatchResponse toResponse(ImportBatch batch, List<ImportRow> rows) {

        int duplicates = (int) rows.stream().filter(ImportRow::isDuplicate).count();
        int fresh = (int) rows.stream()
            .filter(row -> Objects.equals(row.getStatus(), ImportRowStatus.NEW))
            .count();
        return toResponse(batch, fresh, duplicates);
    }

    default ImportBatchDetailResponse toDetail(ImportBatch batch, List<ImportRow> rows,
                                               Map<Long, Long> transactionIdByRow) {

        List<ImportRowResponse> mapped = rows.stream()
            .map(row -> toResponse(row, transactionIdByRow.get(row.getId())))
            .toList();
        ImportBatchResponse batchResponse = toResponse(batch, rows);
        return new ImportBatchDetailResponse(batchResponse, mapped);
    }
}
