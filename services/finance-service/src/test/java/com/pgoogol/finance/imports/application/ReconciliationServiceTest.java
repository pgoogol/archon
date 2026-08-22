package com.pgoogol.finance.imports.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.account.infrastructure.AccountRepository;
import com.pgoogol.finance.imports.domain.ImportBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final LocalDate KONIEC = LocalDate.of(2026, 1, 31);

    @Mock
    private AccountRepository accountRepository;

    @Test
    @DisplayName("reconcile gdy saldo z wyciągu zgadza się z wyliczonym, melduje zgodność")
    void reconcile_whenStatementBalanceMatchesComputed_reportsMatch() {

        // given
        ImportBatch batch = batch(495501L, KONIEC);
        when(accountRepository.findBalanceMinorAsOf(7L, KONIEC)).thenReturn(Optional.of(495501L));

        // when
        ReconciliationService.Reconciliation result = service().reconcile(batch);

        // then
        assertThat(result.matched()).isTrue();
        assertThat(result.differenceMinor()).isZero();
    }

    @Test
    @DisplayName("reconcile gdy salda się rozjeżdżają, pokazuje różnicę zamiast ją poprawiać")
    void reconcile_whenBalancesDiffer_showsDifferenceInsteadOfFixingIt() {

        // given: w bazie brakuje 45,00 względem wyciągu
        ImportBatch batch = batch(495501L, KONIEC);
        when(accountRepository.findBalanceMinorAsOf(7L, KONIEC)).thenReturn(Optional.of(491001L));

        // when
        ReconciliationService.Reconciliation result = service().reconcile(batch);

        // then
        assertThat(result.matched()).isFalse();
        assertThat(result.differenceMinor()).isEqualTo(4500L);
        assertThat(result.statementClosingBalanceMinor()).isEqualTo(495501L);
        assertThat(result.computedBalanceMinor()).isEqualTo(491001L);
    }

    @Test
    @DisplayName("reconcile gdy wyciąg nie podał salda zamknięcia, melduje brak uzgodnienia")
    void reconcile_whenStatementHasNoClosingBalance_reportsNotAvailable() {

        // given
        ImportBatch batch = batch(null, KONIEC);

        // when
        ReconciliationService.Reconciliation result = service().reconcile(batch);

        // then: brak danych to nie to samo co rozjazd — obie liczby zostają puste
        assertThat(result.matched()).isFalse();
        assertThat(result.differenceMinor()).isNull();
        assertThat(result.computedBalanceMinor()).isNull();
    }

    @Test
    @DisplayName("reconcile gdy wyciąg nie podał końca okresu, melduje brak uzgodnienia")
    void reconcile_whenStatementHasNoPeriodEnd_reportsNotAvailable() {

        // given
        ImportBatch batch = batch(495501L, null);

        // when
        ReconciliationService.Reconciliation result = service().reconcile(batch);

        // then
        assertThat(result.matched()).isFalse();
        assertThat(result.statementClosingBalanceMinor()).isNull();
    }

    @Test
    @DisplayName("reconcile gdy konto nie ma jeszcze salda, przyjmuje zero")
    void reconcile_whenAccountHasNoBalanceYet_assumesZero() {

        // given
        ImportBatch batch = batch(1000L, KONIEC);
        when(accountRepository.findBalanceMinorAsOf(7L, KONIEC)).thenReturn(Optional.empty());

        // when
        ReconciliationService.Reconciliation result = service().reconcile(batch);

        // then
        assertThat(result.computedBalanceMinor()).isZero();
        assertThat(result.differenceMinor()).isEqualTo(1000L);
    }

    private ReconciliationService service() {

        return new ReconciliationService(accountRepository);
    }

    private ImportBatch batch(Long closingBalanceMinor, LocalDate periodTo) {

        Account account = FinanceFixtures.account(7L, "Bieżące", FinanceFixtures.PLN);
        ImportBatch batch = new ImportBatch(account, "wyciag.csv", "hash");
        batch.describeStatement(LocalDate.of(2026, 1, 1), periodTo, 0L, closingBalanceMinor, 0);
        return batch;
    }
}
