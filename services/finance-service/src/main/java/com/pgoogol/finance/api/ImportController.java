package com.pgoogol.finance.api;

import org.apache.commons.lang3.StringUtils;
import com.pgoogol.finance.categorization.application.CategorizationService;
import com.pgoogol.finance.categorization.domain.MatchField;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.imports.application.ImportService;
import com.pgoogol.finance.imports.domain.ImportBatch;
import com.pgoogol.finance.imports.domain.ImportRow;
import com.pgoogol.finance.imports.statement.SourceFile;
import com.pgoogol.finance.transaction.application.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/finance/api/v1/imports")
@Tag(name = "imports", description = "Import wyciągów bankowych")
@RequiredArgsConstructor
public class ImportController {

    /**
     * Reguła z poprawki użytkownika ma ustąpić regułom ustawionym ręcznie,
     * a nie przebić je tylko dlatego, że powstała później.
     */
    private static final int DEFAULT_RULE_PRIORITY = 200;

    /** Nazwa zastępcza, gdy przeglądarka nie przysłała nazwy pliku. */
    private static final String FALLBACK_FILE_NAME = "wyciąg";

    private final ImportService importService;
    private final TransactionService transactionService;
    private final CategorizationService categorizationService;
    private final ImportApiMapper mapper;

    @GetMapping
    @Operation(summary = "Wgrane wyciągi, najnowsze pierwsze")
    public List<ImportBatchResponse> listImportBatches(
            @RequestParam(required = false) @Nullable Long accountId) {

        List<ImportBatch> batches = importService.list(accountId);
        return toSummaries(batches);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Wgranie wyciągu i jego sparsowanie",
        description = """
            Pierwszy z dwóch kroków: plik jest liczony, parsowany \
            i deduplikowany, ale żadna transakcja jeszcze nie powstaje. \
            Ten sam plik wgrany drugi raz odpada po skrócie SHA-256 jego \
            zawartości, zanim ktokolwiek go sparsuje.""")
    public ImportBatchResponse uploadStatement(@RequestParam long accountId,
                                               @RequestParam("file") MultipartFile file) {

        SourceFile source = toSourceFile(file);
        ImportBatch batch = importService.upload(accountId, source);
        List<ImportRow> rows = importService.rowsOf(batch.getId());
        return mapper.toResponse(batch, rows);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Podgląd partii wraz z wierszami",
        description = """
            Wiersze wracają w kolejności z pliku, każdy ze statusem \
            i sugerowaną kategorią. Sugestia jest zawsze do potwierdzenia — \
            nic nie zapisuje się samo.""")
    public ImportBatchDetailResponse getImportBatch(@PathVariable long id) {

        ImportBatch batch = importService.get(id);
        List<ImportRow> rows = importService.rowsOf(id);
        Map<Long, Long> transactionIds = transactionIdsOf(rows);
        return mapper.toDetail(batch, rows, transactionIds);
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "Zatwierdzenie partii — wiersze stają się transakcjami",
        description = """
            Wszystko w jednej transakcji bazodanowej: albo powstają wszystkie \
            transakcje, albo żadna. Wiersze rozpoznane jako duplikaty są \
            pomijane. Odpowiedź niesie uzgodnienie salda — rozjazd jest \
            pokazywany, nigdy poprawiany automatycznie.""")
    public CommitImportResponse commitImportBatch(@PathVariable long id,
                                                  @Valid @RequestBody CommitImportRequest request) {

        Map<Long, Long> categoryByRow = toCategoryMap(request);
        Map<Long, Long> occurrenceByRow = toOccurrenceMap(request);
        ImportService.CommitResult result =
            importService.commit(id, categoryByRow, occurrenceByRow);
        rememberCorrections(request);
        return mapper.toResponse(result);
    }

    /**
     * Reguły powstają dopiero po udanym zatwierdzeniu. Zapamiętanie wzorca
     * z żądania, które zaraz odpadnie na walidacji, zostawiłoby regułę po
     * imporcie, którego nie było.
     */
    private void rememberCorrections(CommitImportRequest request) {

        request.categoryAssignments().forEach(this::rememberCorrection);
    }

    private void rememberCorrection(ImportCategoryAssignment assignment) {

        String pattern = assignment.rememberPattern();
        if (StringUtils.isBlank(pattern)) {

            return;
        }
        categorizationService.rememberCorrection(pattern, MatchField.ANY,
            assignment.categoryId(), DEFAULT_RULE_PRIORITY);
    }

    private Map<Long, Long> toOccurrenceMap(CommitImportRequest request) {

        List<ImportOccurrenceAssignment> assignments =
            Objects.requireNonNullElse(request.occurrenceAssignments(), List.of());
        Map<Long, Long> byRow = new LinkedHashMap<>();
        assignments.forEach(assignment -> byRow.put(assignment.rowId(),
            assignment.occurrenceId()));
        return Map.copyOf(byRow);
    }

    /**
     * Liczniki dla całej listy jednym zapytaniem. Pobieranie wierszy każdej
     * partii z osobna dawałoby zapytanie na pozycję ekranu.
     */
    private List<ImportBatchResponse> toSummaries(List<ImportBatch> batches) {

        List<Long> ids = batches.stream().map(ImportBatch::getId).toList();
        Map<Long, ImportService.RowCounts> counts = importService.countsOf(ids);
        return batches.stream().map(batch -> toSummary(batch, counts)).toList();
    }

    private ImportBatchResponse toSummary(ImportBatch batch,
                                          Map<Long, ImportService.RowCounts> counts) {

        ImportService.RowCounts rowCounts =
            counts.getOrDefault(batch.getId(), new ImportService.RowCounts(0, 0));
        return mapper.toResponse(batch, rowCounts.newCount(), rowCounts.duplicateCount());
    }

    private Map<Long, Long> transactionIdsOf(List<ImportRow> rows) {

        List<Long> rowIds = rows.stream().map(ImportRow::getId).toList();
        return transactionService.transactionIdsByImportRow(rowIds);
    }

    /**
     * Dwa przypisania do tego samego wiersza to sprzeczne polecenie, nie
     * literówka do cichego rozstrzygnięcia — {@code Collectors.toMap} rzuciłby
     * tu {@code IllegalStateException}, czyli 500 zamiast 400.
     */
    private Map<Long, Long> toCategoryMap(CommitImportRequest request) {

        Map<Long, Long> byRow = new LinkedHashMap<>();
        request.categoryAssignments().forEach(assignment -> putOnce(byRow, assignment));
        return Map.copyOf(byRow);
    }

    private void putOnce(Map<Long, Long> byRow, ImportCategoryAssignment assignment) {

        Long previous = byRow.put(assignment.rowId(), assignment.categoryId());
        if (Objects.nonNull(previous)) {

            throw new ValidationException(ErrorCodes.VALIDATION_FAILED,
                ExceptionMessageConstants.IMPORT_ROW_ASSIGNED_TWICE
                    .formatted(assignment.rowId()));
        }
    }

    private SourceFile toSourceFile(MultipartFile file) {

        if (file.isEmpty()) {

            throw new ValidationException(ErrorCodes.STATEMENT_EMPTY,
                ExceptionMessageConstants.STATEMENT_FILE_EMPTY);
        }
        try {

            return new SourceFile(originalName(file), file.getBytes());
        } catch (IOException ex) {

            throw new ValidationException(ErrorCodes.STATEMENT_UNREADABLE,
                ExceptionMessageConstants.STATEMENT_UNREADABLE
                    .formatted(originalName(file), ex.getMessage()));
        }
    }

    private String originalName(MultipartFile file) {

        String name = file.getOriginalFilename();
        if (StringUtils.isBlank(name)) {

            return FALLBACK_FILE_NAME;
        }
        return name;
    }
}
