package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.TransactionOutcome;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Strategia przechwytywania transakcji Springa. Boot sam podpina każdy bean
 * {@link TransactionExecutionListener} do menedżera transakcji, więc wystarczy, że ta
 * klasa jest beanem.
 *
 * <p>Menedżer woła słuchacza tylko dla transakcji, które faktycznie zaczyna: zewnętrznej,
 * {@code REQUIRES_NEW} i {@code NESTED}. Metoda dołączająca do trwającej transakcji nie
 * daje osobnego zdarzenia, a jej operacje liczą się do tej trwającej. Nieudany początek
 * nie zostawia transakcji na stosie.</p>
 *
 * <p>Silnik przychodzi jako dostawca, bo słuchacz trafia do menedżera transakcji bardzo
 * wcześnie; pobranie silnika od razu wymuszałoby budowę analiz przed infrastrukturą
 * danych. Własny błąd zostaje w logu na DEBUG i nie przerywa transakcji aplikacji.</p>
 */
public class TransactionCaptureStrategy implements TransactionExecutionListener {

    static final String UNNAMED = "(bez nazwy)";

    private static final Logger log = LoggerFactory.getLogger(TransactionCaptureStrategy.class);

    private final Supplier<DiagnosticsEngine> engine;

    public TransactionCaptureStrategy(Supplier<DiagnosticsEngine> engine) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
    }

    @Override
    public void afterBegin(TransactionExecution transaction, @Nullable Throwable beginFailure) {

        if (Objects.nonNull(beginFailure)) {

            return;
        }
        String name = Objects.requireNonNullElse(transaction.getTransactionName(), "");
        if (name.isBlank()) {

            name = UNNAMED;
        }
        String chosen = name;
        safely(() -> engine.get().beginTransaction(chosen, transaction.isReadOnly()));
    }

    @Override
    public void afterCommit(TransactionExecution transaction, @Nullable Throwable commitFailure) {

        TransactionOutcome outcome = TransactionOutcome.COMMITTED;
        if (Objects.nonNull(commitFailure)) {

            outcome = TransactionOutcome.ROLLED_BACK;
        }
        TransactionOutcome chosen = outcome;
        safely(() -> engine.get().endTransaction(chosen));
    }

    @Override
    public void afterRollback(TransactionExecution transaction, @Nullable Throwable rollbackFailure) {

        safely(() -> engine.get().endTransaction(TransactionOutcome.ROLLED_BACK));
    }

    private void safely(Runnable action) {

        try {

            action.run();
        } catch (RuntimeException | LinkageError failure) {

            log.debug("Nie udało się przechwycić transakcji", failure);
        }
    }
}
