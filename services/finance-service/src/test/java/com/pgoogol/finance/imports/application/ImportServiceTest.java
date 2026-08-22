package com.pgoogol.finance.imports.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.common.ConflictException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    private static final long KONTO = 7L;
    private static final LocalDate DZIEN = LocalDate.of(2026, 1, 5);

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private ImportRowRepository importRowRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private RowSuggestionService rowSuggestionService;

    @Mock
    private OccurrenceService occurrenceService;

    @Mock
    private StatementParser parser;

    private final Account account = FinanceFixtures.account(KONTO, "Bieżące", FinanceFixtures.PLN);

    @Test
    @DisplayName("upload gdy ten sam plik był już wgrany, odrzuca go przed parsowaniem")
    void upload_whenSameFileWasAlreadyUploaded_rejectsBeforeParsing() {

        // given
        SourceFile file = file("cokolwiek");
        when(accountService.get(KONTO)).thenReturn(account);
        when(importBatchRepository.findByAccountIdAndFileHash(KONTO, file.fileHash()))
            .thenReturn(Optional.of(batch()));

        // when & then
        assertThatThrownBy(() -> service().upload(KONTO, file))
            .isInstanceOf(ConflictException.class);
        verify(parser, never()).parse(any(), anyInt());
    }

    @Test
    @DisplayName("upload gdy formatu nie rozpoznaje żaden parser, kończy się błędem wejścia")
    void upload_whenNoParserRecognisesFormat_failsAsInputError() {

        // given
        SourceFile file = file("obcy format");
        prepareUpload();
        when(parser.supports(file)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service().upload(KONTO, file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("upload gdy plik nie ma żadnej operacji, kończy się błędem wejścia")
    void upload_whenFileHasNoOperations_failsAsInputError() {

        // given
        SourceFile file = file("pusty");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt()))
            .thenReturn(new ParsedStatement(null, null, null, null, List.of()));

        // when & then
        assertThatThrownBy(() -> service().upload(KONTO, file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("upload gdy parser wywala się na treści, zamienia to na błąd wejścia")
    void upload_whenParserFailsOnContent_translatesToInputError() {

        // given: plik przychodzi od użytkownika, więc literówka w kwocie nie ma
        // prawa dać 500
        SourceFile file = file("zepsuty");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt()))
            .thenThrow(new IllegalArgumentException("Nie jest kwotą: abc"));

        // when & then
        assertThatThrownBy(() -> service().upload(KONTO, file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("upload gdy wyciąg jest w innej walucie niż konto, kończy się błędem wejścia")
    void upload_whenStatementCurrencyDiffersFromAccount_failsAsInputError() {

        // given
        SourceFile file = file("walutowy");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt()))
            .thenReturn(statement(row(0, -4500L, "EUR", "Kawa", null)));

        // when & then
        assertThatThrownBy(() -> service().upload(KONTO, file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("upload gdy wyciąg niesie walutę zgodną z kontem, przyjmuje wiersz")
    void upload_whenStatementCurrencyMatchesAccount_acceptsRow() {

        // given
        SourceFile file = file("zgodny");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt()))
            .thenReturn(statement(row(0, -4500L, FinanceFixtures.PLN, "Kawa", null)));

        // when
        service().upload(KONTO, file);

        // then
        assertThat(capturedRows().getFirst().getCurrency()).isEqualTo(FinanceFixtures.PLN);
    }

    @Test
    @DisplayName("upload gdy dwa wiersze są identyczne, daje im różne klucze deduplikacji")
    void upload_whenTwoRowsAreIdentical_givesThemDifferentDedupKeys() {

        // given: dwie takie same kawy tego samego dnia to dwie operacje
        SourceFile file = file("dwie kawy");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt())).thenReturn(statement(
            row(0, -1200L, null, "Kawa", null),
            row(1, -1200L, null, "Kawa", null)));

        // when
        service().upload(KONTO, file);

        // then
        List<ImportRow> saved = capturedRows();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getDedupKey()).isNotEqualTo(saved.get(1).getDedupKey());
        assertThat(saved).extracting(ImportRow::getStatus)
            .containsOnly(ImportRowStatus.NEW);
    }

    @Test
    @DisplayName("upload gdy wiersz był już zaimportowany, oznacza go jako duplikat")
    void upload_whenRowWasAlreadyImported_marksItAsDuplicate() {

        // given
        SourceFile file = file("nachodzący zakres");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt()))
            .thenReturn(statement(row(0, -1200L, null, "Kawa", null)));
        when(importRowRepository.existsActiveByDedupKey(anyString(), any())).thenReturn(true);

        // when
        service().upload(KONTO, file);

        // then
        assertThat(capturedRows().getFirst().getStatus()).isEqualTo(ImportRowStatus.DUPLICATE);
    }

    @Test
    @DisplayName("upload gdy wiersz ma referencję bankową, buduje klucz z niej")
    void upload_whenRowHasBankReference_buildsKeyFromIt() {

        // given
        SourceFile file = file("z referencjami");
        prepareUpload();
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(any(), anyInt()))
            .thenReturn(statement(row(0, -1200L, null, "Kawa", "REF-001")));

        // when
        service().upload(KONTO, file);

        // then
        String oczekiwany = new DedupKey().fromBankReference(KONTO, "REF-001");
        assertThat(capturedRows().getFirst().getDedupKey()).isEqualTo(oczekiwany);
    }

    @Test
    @DisplayName("commit gdy wiersz jest wydatkiem, tworzy transakcję typu EXPENSE")
    void commit_whenRowIsOutgoing_createsExpenseTransaction() {

        // given
        ImportBatch batch = batch();
        ImportRow row = savedRow(batch, -4500L, ImportRowStatus.NEW);
        prepareCommit(batch, row);

        // when
        service().commit(1L, Map.of(11L, 3L), Map.of());

        // then
        TransactionCommand command = capturedCommand();
        assertThat(command.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(command.amountMinor()).isEqualTo(4500L);
        assertThat(command.categoryId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("commit gdy wiersz jest wpływem, tworzy transakcję typu INCOME")
    void commit_whenRowIsIncoming_createsIncomeTransaction() {

        // given
        ImportBatch batch = batch();
        ImportRow row = savedRow(batch, 500000L, ImportRowStatus.NEW);
        prepareCommit(batch, row);

        // when
        service().commit(1L, Map.of(11L, 3L), Map.of());

        // then
        assertThat(capturedCommand().type()).isEqualTo(TransactionType.INCOME);
    }

    @Test
    @DisplayName("commit gdy wiersz nie ma kategorii, wywala całą partię")
    void commit_whenRowHasNoCategory_failsWholeBatch() {

        // given
        ImportBatch batch = batch();
        ImportRow row = savedRow(batch, -4500L, ImportRowStatus.NEW);
        prepareCommit(batch, row);

        // when & then
        assertThatThrownBy(() -> service().commit(1L, Map.of(), Map.of()))
            .isInstanceOf(ValidationException.class);
        assertThat(batch.isCommitted()).isFalse();
    }

    @Test
    @DisplayName("commit pomija wiersze rozpoznane jako duplikaty")
    void commit_skipsRowsMarkedAsDuplicates() {

        // given
        ImportBatch batch = batch();
        ImportRow duplikat = savedRow(batch, -4500L, ImportRowStatus.DUPLICATE);
        prepareCommit(batch, duplikat);

        // when
        ImportService.CommitResult result = service().commit(1L, Map.of(), Map.of());

        // then
        assertThat(result.committedCount()).isZero();
        assertThat(result.skippedDuplicateCount()).isEqualTo(1);
        verify(transactionService, never()).create(any());
    }

    @Test
    @DisplayName("commit gdy partia była już zatwierdzona, odrzuca powtórkę")
    void commit_whenBatchWasAlreadyCommitted_rejectsRepeat() {

        // given
        ImportBatch batch = batch();
        batch.markCommitted();
        when(importBatchRepository.findDetailedById(1L)).thenReturn(Optional.of(batch));

        // when & then
        assertThatThrownBy(() -> service().commit(1L, Map.of(), Map.of()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("countsOf gdy nie ma partii, nie odpytuje bazy")
    void countsOf_whenThereAreNoBatches_doesNotQueryDatabase() {

        // when
        Map<Long, ImportService.RowCounts> counts = service().countsOf(List.of());

        // then
        assertThat(counts).isEmpty();
        verify(importRowRepository, never()).countByStatusForBatches(any());
    }

    @Test
    @DisplayName("countsOf sumuje wiersze per partia i status")
    void countsOf_sumsRowsPerBatchAndStatus() {

        // given: status COMMITTED nie wchodzi do żadnego z dwóch liczników —
        // partia zatwierdzona nie ma już nic „do wejścia"
        when(importRowRepository.countByStatusForBatches(List.of(1L, 2L))).thenReturn(List.of(
            statusCount(1L, ImportRowStatus.NEW, 3),
            statusCount(1L, ImportRowStatus.DUPLICATE, 2),
            statusCount(2L, ImportRowStatus.COMMITTED, 5)));

        // when
        Map<Long, ImportService.RowCounts> counts = service().countsOf(List.of(1L, 2L));

        // then
        assertThat(counts.get(1L).newCount()).isEqualTo(3);
        assertThat(counts.get(1L).duplicateCount()).isEqualTo(2);
        assertThat(counts.get(2L).newCount()).isZero();
        assertThat(counts.get(2L).duplicateCount()).isZero();
    }

    private ImportRowRepository.StatusCount statusCount(long batchId, ImportRowStatus status,
                                                        long total) {

        return new ImportRowRepository.StatusCount() {

            @Override
            public Long getBatchId() {

                return batchId;
            }

            @Override
            public ImportRowStatus getStatus() {

                return status;
            }

            @Override
            public long getTotal() {

                return total;
            }
        };
    }

    private ImportService service() {

        return new ImportService(importBatchRepository, importRowRepository, accountService,
            currencyService, transactionService, reconciliationService, rowSuggestionService,
            occurrenceService, new DedupKey(), List.of(parser));
    }

    private void prepareUpload() {

        when(accountService.get(KONTO)).thenReturn(account);
        when(importBatchRepository.findByAccountIdAndFileHash(eq(KONTO), anyString()))
            .thenReturn(Optional.empty());
        when(currencyService.minorUnitsOf(FinanceFixtures.PLN)).thenReturn(new MinorUnits(2));
        when(importBatchRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void prepareCommit(ImportBatch batch, ImportRow row) {

        when(importBatchRepository.findDetailedById(1L)).thenReturn(Optional.of(batch));
        when(importRowRepository.findByBatchIdOrdered(1L)).thenReturn(List.of(row));
        when(transactionService.create(any())).thenReturn(transaction());
        when(reconciliationService.reconcile(batch))
            .thenReturn(new ReconciliationService.Reconciliation(null, null, null, false));
    }

    @SuppressWarnings("unchecked")
    private List<ImportRow> capturedRows() {

        ArgumentCaptor<List<ImportRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(importRowRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private TransactionCommand capturedCommand() {

        ArgumentCaptor<TransactionCommand> captor =
            ArgumentCaptor.forClass(TransactionCommand.class);
        verify(transactionService).create(captor.capture());
        return captor.getValue();
    }

    private ParsedStatement statement(RawRow... rows) {

        return new ParsedStatement(DZIEN, DZIEN, 0L, 0L, List.of(rows));
    }

    private RawRow row(int ordinal, long amountMinor, String currency, String description,
                       String bankReference) {

        return new RawRow(ordinal, DZIEN, amountMinor, currency, null, null, description, null,
            bankReference);
    }

    private ImportBatch batch() {

        return FinanceFixtures.importBatch(1L, account, "hash-pliku");
    }

    private ImportRow savedRow(ImportBatch batch, long amountMinor, ImportRowStatus status) {

        RawRow raw = row(0, amountMinor, null, "Kawa", null);
        return FinanceFixtures.importRow(11L, batch, raw, "klucz", status);
    }

    private Transaction transaction() {

        return new Transaction(TransactionType.EXPENSE, DZIEN, 4500L, FinanceFixtures.PLN, account);
    }

    private SourceFile file(String content) {

        return new SourceFile("wyciag.csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
