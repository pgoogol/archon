package com.pgoogol.finance.imports.application;

import com.pgoogol.finance.account.infrastructure.AccountRepository;
import com.pgoogol.finance.imports.domain.ImportBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Porównanie salda zamknięcia z wyciągu z saldem wyliczonym z transakcji.
 *
 * <p>Rozjazd jest <b>pokazywany, nigdy poprawiany</b>. Automatyczne „wyrównanie"
 * saldem z pliku zamieniłoby błąd, który widać, w błąd, którego nie widać —
 * a przy pieniądzach to najgorszy możliwy kierunek.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReconciliationService {

    private final AccountRepository accountRepository;

    public Reconciliation reconcile(ImportBatch batch) {

        Objects.requireNonNull(batch, "batch");
        Long statementClosing = batch.getClosingBalanceMinor();
        LocalDate periodTo = batch.getPeriodTo();
        if (Objects.isNull(statementClosing) || Objects.isNull(periodTo)) {

            // wyciąg bez salda zamknięcia albo bez daty końca okresu nie daje
            // się uzgodnić — to nie jest rozjazd, to brak danych
            return Reconciliation.notAvailable();
        }
        long accountId = batch.getAccount().getId();
        long computed = accountRepository.findBalanceMinorAsOf(accountId, periodTo)
            .orElse(0L);
        return Reconciliation.of(statementClosing, computed);
    }

    /**
     * @param differenceMinor saldo z wyciągu minus saldo wyliczone; dodatnia
     *                        wartość znaczy, że w bazie czegoś brakuje
     */
    public record Reconciliation(
            Long statementClosingBalanceMinor,
            Long computedBalanceMinor,
            Long differenceMinor,
            boolean matched) {

        static Reconciliation notAvailable() {

            return new Reconciliation(null, null, null, false);
        }

        static Reconciliation of(long statementClosing, long computed) {

            long difference = statementClosing - computed;
            return new Reconciliation(statementClosing, computed, difference, difference == 0);
        }
    }
}
