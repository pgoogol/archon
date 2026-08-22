package com.pgoogol.finance.imports.application;

import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.imports.domain.ImportBatch;
import com.pgoogol.finance.imports.domain.ImportRow;
import com.pgoogol.finance.imports.domain.ImportRowStatus;
import com.pgoogol.finance.imports.infrastructure.ImportBatchRepository;
import com.pgoogol.finance.imports.infrastructure.ImportRowRepository;
import com.pgoogol.finance.imports.statement.DedupKey;
import com.pgoogol.finance.recurring.application.OccurrenceService;
import com.pgoogol.finance.imports.statement.ParsedStatement;
import com.pgoogol.finance.imports.statement.RawRow;
import com.pgoogol.finance.imports.statement.SourceFile;
import com.pgoogol.finance.imports.statement.StatementParser;
import com.pgoogol.finance.transaction.application.TransactionCommand;
import com.pgoogol.finance.transaction.application.TransactionService;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Import wyciągu w dwóch krokach.
 *
 * <p><b>Wgranie</b> liczy skrót pliku, parsuje go, deduplikuje wiersze i zapisuje
 * partię — ale nie tworzy ani jednej transakcji. <b>Zatwierdzenie</b> zamienia
 * wiersze na transakcje w jednej transakcji bazodanowej: albo powstaną wszystkie,
 * albo żadna.</p>
 *
 * <p>Rozdzielenie tych kroków jest tu istotą sprawy, nie wygodą interfejsu:
 * import wyciągu jest nieodwracalny w praktyce (nikt nie wycofa ręcznie stu
 * transakcji), więc użytkownik musi zobaczyć, co się stanie, zanim się stanie.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImportBatchRepository importBatchRepository;
    private final ImportRowRepository importRowRepository;
    private final AccountService accountService;
    private final CurrencyService currencyService;
    private final TransactionService transactionService;
    private final ReconciliationService reconciliationService;
    private final RowSuggestionService rowSuggestionService;
    private final OccurrenceService occurrenceService;
    private final DedupKey dedupKey;
    private final List<StatementParser> parsers;

    public List<ImportBatch> list(@Nullable Long accountId) {

        return importBatchRepository.findAllForAccount(accountId);
    }

    public ImportBatch get(long id) {

        return importBatchRepository.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCodes.IMPORT_BATCH_NOT_FOUND,
                ExceptionMessageConstants.IMPORT_BATCH_NOT_FOUND.formatted(id)));
    }

    public List<ImportRow> rowsOf(long batchId) {

        return importRowRepository.findByBatchIdOrdered(batchId);
    }

    /**
     * Liczniki wierszy dla wielu partii naraz — jedno zapytanie na całą listę
     * wyciągów zamiast jednego na pozycję.
     */
    public Map<Long, RowCounts> countsOf(List<Long> batchIds) {

        if (batchIds.isEmpty()) {

            return Map.of();
        }
        List<ImportRowRepository.StatusCount> counts =
            importRowRepository.countByStatusForBatches(batchIds);
        Map<Long, RowCounts> byBatch = new HashMap<>();
        counts.forEach(count -> accumulate(byBatch, count));
        return Map.copyOf(byBatch);
    }

    private void accumulate(Map<Long, RowCounts> byBatch, ImportRowRepository.StatusCount count) {

        RowCounts current = byBatch.getOrDefault(count.getBatchId(), RowCounts.empty());
        byBatch.put(count.getBatchId(), current.plus(count.getStatus(), count.getTotal()));
    }

    /**
     * Krok pierwszy: plik wchodzi do bazy jako partia z wierszami, żadna
     * transakcja jeszcze nie powstaje.
     */
    @Transactional
    public ImportBatch upload(long accountId, SourceFile file) {

        Account account = accountService.get(accountId);
        String fileHash = file.fileHash();
        rejectWhenAlreadyUploaded(accountId, fileHash, file.name());

        StatementParser parser = parserFor(file);
        MinorUnits minorUnits = currencyService.minorUnitsOf(account.getCurrency());
        ParsedStatement statement = parse(parser, file, minorUnits);

        ImportBatch batch = new ImportBatch(account, file.name(), fileHash);
        batch.describeStatement(statement.periodFrom(), statement.periodTo(),
            statement.openingBalanceMinor(), statement.closingBalanceMinor(),
            statement.rows().size());
        ImportBatch saved = importBatchRepository.save(batch);

        List<ImportRow> rows = toRows(saved, account, statement);
        List<ImportRow> stored = importRowRepository.saveAll(rows);
        // podpowiedzi liczymy po zapisie, żeby wiersz miał już identyfikator —
        // podgląd pokazuje je obok siebie, a potwierdza je człowiek
        rowSuggestionService.suggestFor(accountId, stored);
        log.info("Wgrano wyciąg {} dla konta {}: {} wierszy, w tym {} duplikatów",
            file.name(), accountId, rows.size(), countDuplicates(rows));
        return saved;
    }

    /**
     * Krok drugi: wiersze o statusie {@code NEW} stają się transakcjami.
     * Wszystko w jednej transakcji bazodanowej — wyciąg zaimportowany w połowie
     * byłby gorszy niż niezaimportowany wcale.
     */
    @Transactional
    public CommitResult commit(long batchId, Map<Long, Long> categoryByRowId,
                               Map<Long, Long> occurrenceByRowId) {

        Objects.requireNonNull(categoryByRowId, "categoryByRowId");
        Objects.requireNonNull(occurrenceByRowId, "occurrenceByRowId");
        ImportBatch batch = get(batchId);
        rejectWhenCommitted(batch);

        List<ImportRow> rows = rowsOf(batchId);
        List<ImportRow> pending = rows.stream()
            .filter(row -> Objects.equals(row.getStatus(), ImportRowStatus.NEW))
            .toList();
        pending.forEach(row -> commitRow(row, batch, categoryByRowId, occurrenceByRowId));

        batch.markCommitted();
        long duplicates = countDuplicates(rows);
        log.info("Zatwierdzono partię {}: {} transakcji, {} wierszy pominiętych jako duplikaty",
            batchId, pending.size(), duplicates);
        ReconciliationService.Reconciliation reconciliation = reconciliationService.reconcile(batch);
        return new CommitResult(batchId, pending.size(), (int) duplicates, reconciliation);
    }

    private void commitRow(ImportRow row, ImportBatch batch, Map<Long, Long> categoryByRowId,
                           Map<Long, Long> occurrenceByRowId) {

        Long categoryId = categoryByRowId.get(row.getId());
        requireCategory(row, categoryId);
        TransactionCommand command = toCommand(row, batch, categoryId);
        Transaction created = transactionService.create(command);
        created.assignImportRow(row);
        row.markCommitted();
        settleOccurrence(row, created, occurrenceByRowId);
    }

    /**
     * Rozliczenie rachunku cyklicznego tym wierszem — wyłącznie wtedy, gdy
     * użytkownik potwierdził je w żądaniu. Sama podpowiedź na wierszu niczego
     * nie rozlicza.
     */
    private void settleOccurrence(ImportRow row, Transaction created,
                                  Map<Long, Long> occurrenceByRowId) {

        Long occurrenceId = occurrenceByRowId.get(row.getId());
        if (Objects.isNull(occurrenceId)) {

            return;
        }
        occurrenceService.settleWith(occurrenceId, row.getBookedOn(),
            row.absoluteAmountMinor(), created);
    }

    private TransactionCommand toCommand(ImportRow row, ImportBatch batch, Long categoryId) {

        return new TransactionCommand(
            typeOf(row),
            row.getBookedOn(),
            row.absoluteAmountMinor(),
            row.getCurrency(),
            row.getOriginalAmountMinor(),
            row.getOriginalCurrency(),
            batch.getAccount().getId(),
            null,
            null,
            categoryId,
            row.getDescription(),
            row.getCounterparty());
    }

    /**
     * Jedyne miejsce w module, w którym znak z wyciągu zamienia się na typ
     * transakcji. Rozsypane po parserach dałoby tyle interpretacji, ile banków.
     *
     * <p>Transferu tędy nie rozpoznajemy — para przelewów między własnymi kontami
     * wygląda w pliku jak zwykły wydatek i wpływ, a scalenie ich w jedną operację
     * to zadanie wykrywania transferów, zawsze do potwierdzenia przez człowieka.</p>
     */
    private TransactionType typeOf(ImportRow row) {

        if (row.isOutgoing()) {

            return TransactionType.EXPENSE;
        }
        return TransactionType.INCOME;
    }

    private void requireCategory(ImportRow row, @Nullable Long categoryId) {

        if (Objects.isNull(categoryId)) {

            throw new ValidationException(ErrorCodes.CATEGORY_REQUIRED,
                ExceptionMessageConstants.IMPORT_ROW_WITHOUT_CATEGORY.formatted(row.getOrdinal()));
        }
    }

    private void rejectWhenAlreadyUploaded(long accountId, String fileHash, String fileName) {

        boolean alreadyUploaded = importBatchRepository
            .findByAccountIdAndFileHash(accountId, fileHash)
            .isPresent();
        if (alreadyUploaded) {

            throw new ConflictException(ErrorCodes.STATEMENT_ALREADY_IMPORTED,
                ExceptionMessageConstants.STATEMENT_ALREADY_IMPORTED.formatted(fileName));
        }
    }

    private void rejectWhenCommitted(ImportBatch batch) {

        if (batch.isCommitted()) {

            throw new ConflictException(ErrorCodes.IMPORT_BATCH_ALREADY_COMMITTED,
                ExceptionMessageConstants.IMPORT_BATCH_ALREADY_COMMITTED.formatted(batch.getId()));
        }
    }

    private StatementParser parserFor(SourceFile file) {

        return parsers.stream()
            .filter(parser -> parser.supports(file))
            .findFirst()
            .orElseThrow(() -> new ValidationException(ErrorCodes.STATEMENT_FORMAT_UNKNOWN,
                ExceptionMessageConstants.STATEMENT_FORMAT_UNKNOWN.formatted(file.name())));
    }

    private ParsedStatement parse(StatementParser parser, SourceFile file, MinorUnits minorUnits) {

        ParsedStatement statement = parseGuarded(parser, file, minorUnits);
        if (statement.isEmpty()) {

            throw new ValidationException(ErrorCodes.STATEMENT_EMPTY,
                ExceptionMessageConstants.STATEMENT_EMPTY.formatted(file.name()));
        }
        return statement;
    }

    /**
     * Plik przychodzi od użytkownika, więc każdy błąd parsowania jest błędem
     * wejścia, nie awarią serwisu — inaczej literówka w kwocie dawałaby 500.
     */
    private ParsedStatement parseGuarded(StatementParser parser, SourceFile file,
                                         MinorUnits minorUnits) {

        try {

            return parser.parse(file, minorUnits.scale());
        } catch (IllegalArgumentException ex) {

            throw new ValidationException(ErrorCodes.STATEMENT_UNREADABLE,
                ExceptionMessageConstants.STATEMENT_UNREADABLE.formatted(file.name(),
                    ex.getMessage()));
        }
    }

    /**
     * Klucz deduplikacji i status dla każdego wiersza.
     *
     * <p>Numer kolejny liczy się <b>w obrębie pliku</b>, wśród wierszy o tej samej
     * dacie, kwocie i znormalizowanym opisie. Dzięki temu dwie identyczne kawy
     * tego samego dnia dostają numery 0 i 1 i zostają dwiema transakcjami,
     * a ten sam plik wgrany drugi raz trafia w te same numery i cały wypada
     * jako duplikat.</p>
     */
    private List<ImportRow> toRows(ImportBatch batch, Account account, ParsedStatement statement) {

        List<RawRow> rawRows = statement.rows();
        Map<String, Integer> seenInFile = new HashMap<>();
        List<ImportRow> rows = new ArrayList<>(rawRows.size());
        rawRows.forEach(raw -> rows.add(toRow(batch, account, raw, seenInFile)));
        return List.copyOf(rows);
    }

    private ImportRow toRow(ImportBatch batch, Account account, RawRow raw,
                            Map<String, Integer> seenInFile) {

        String currency = currencyOf(raw, account);
        String key = keyFor(account.getId(), raw, seenInFile);
        ImportRowStatus status = statusFor(key);
        return new ImportRow(batch, raw, currency, key, status);
    }

    private String keyFor(long accountId, RawRow raw, Map<String, Integer> seenInFile) {

        if (raw.hasBankReference()) {

            return dedupKey.fromBankReference(accountId, raw.bankReference());
        }
        String base = "%s|%d|%s".formatted(
            raw.bookedOn(), raw.amountMinor(), dedupKey.normalize(raw.description()));
        int ordinal = seenInFile.merge(base, 1, Integer::sum) - 1;
        return dedupKey.fromContent(accountId, raw.bookedOn(), raw.amountMinor(),
            raw.description(), ordinal);
    }

    private ImportRowStatus statusFor(String key) {

        boolean alreadyImported =
            importRowRepository.existsActiveByDedupKey(key, ImportRowStatus.DUPLICATE);
        if (alreadyImported) {

            return ImportRowStatus.DUPLICATE;
        }
        return ImportRowStatus.NEW;
    }

    /**
     * Wyciąg konta walutowego może nieść kod waluty przy kwocie. Gdy niesie
     * i nie zgadza się z walutą konta, plik trafił pod zły rachunek.
     */
    private String currencyOf(RawRow raw, Account account) {

        String fromFile = raw.currency();
        if (Objects.isNull(fromFile) || Objects.equals(fromFile, account.getCurrency())) {

            return account.getCurrency();
        }
        throw new ValidationException(ErrorCodes.CURRENCY_MISMATCH,
            ExceptionMessageConstants.STATEMENT_CURRENCY_MISMATCH.formatted(
                fromFile, account.getCurrency()));
    }

    private long countDuplicates(List<ImportRow> rows) {

        return rows.stream().filter(ImportRow::isDuplicate).count();
    }

    /** Ile wierszy partii wejdzie do bazy, a ile odpadnie jako duplikaty. */
    public record RowCounts(int newCount, int duplicateCount) {

        static RowCounts empty() {

            return new RowCounts(0, 0);
        }

        RowCounts plus(ImportRowStatus status, long total) {

            if (Objects.equals(status, ImportRowStatus.NEW)) {

                return new RowCounts(newCount + (int) total, duplicateCount);
            }
            if (Objects.equals(status, ImportRowStatus.DUPLICATE)) {

                return new RowCounts(newCount, duplicateCount + (int) total);
            }
            return this;
        }
    }

    /** Wynik zatwierdzenia partii wraz z uzgodnieniem salda. */
    public record CommitResult(
            long batchId,
            int committedCount,
            int skippedDuplicateCount,
            ReconciliationService.Reconciliation reconciliation) {

    }
}
