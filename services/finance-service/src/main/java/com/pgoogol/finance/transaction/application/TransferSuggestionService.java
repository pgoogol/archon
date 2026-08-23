package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import com.pgoogol.finance.transaction.infrastructure.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wykrywanie przelewów między własnymi kontami.
 *
 * <p>Przelew z konta na konto przychodzi w dwóch wyciągach jako zwykły wydatek
 * i zwykły wpływ. Zostawione tak, zawyżają naraz wydatki i przychody — dlatego
 * szukamy par i <b>proponujemy</b> scalenie ich w jedną operację typu
 * {@code TRANSFER}.</p>
 *
 * <p>Scalenie jest zawsze osobnym, potwierdzonym krokiem. Automatyczne łączenie
 * zjadłoby dwie prawdziwe operacje o tej samej kwocie w tym samym tygodniu —
 * a przy pieniądzach cichy błąd jest gorszy niż widoczny brak.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransferSuggestionService {

    /** Przelew wewnętrzny księguje się po obu stronach w ciągu kilku dni. */
    static final int MAX_DAYS_APART = 3;

    private static final Logger log = LoggerFactory.getLogger(TransferSuggestionService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    /**
     * Pary wyglądające na dwie strony jednego przelewu: ta sama kwota i waluta,
     * przeciwne kierunki, dwa różne własne konta, odstęp nie większy niż
     * {@value #MAX_DAYS_APART} dni.
     */
    public List<TransferCandidate> candidates(LocalDate from, LocalDate to) {

        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        List<Transaction> flows = transactionRepository.findFlowsBetween(from, to);
        List<Transaction> expenses = flows.stream().filter(this::isExpense).toList();
        List<Transaction> incomes = flows.stream().filter(this::isIncome).toList();

        Set<Long> usedIncomes = new HashSet<>();
        List<TransferCandidate> candidates = new ArrayList<>();
        expenses.forEach(expense -> pairWith(expense, incomes, usedIncomes, candidates));
        return List.copyOf(candidates);
    }

    /**
     * Zamienia potwierdzoną parę w jedną transakcję {@code TRANSFER} i usuwa
     * obie strony. Kwota i daty muszą nadal spełniać warunki pary — między
     * podglądem a potwierdzeniem ktoś mógł je zmienić.
     */
    @Transactional
    public Transaction merge(long expenseId, long incomeId) {

        Transaction expense = transactionService.get(expenseId);
        Transaction income = transactionService.get(incomeId);
        requirePair(expense, income);

        TransactionCommand command = transferCommand(expense, income);
        Transaction transfer = transactionService.create(command);
        transactionRepository.delete(expense);
        transactionRepository.delete(income);
        log.info("Scalono transakcje {} i {} w przelew {}", expenseId, incomeId, transfer.getId());
        return transfer;
    }

    private TransactionCommand transferCommand(Transaction expense, Transaction income) {

        Account fromAccount = expense.getAccount();
        Account toAccount = income.getAccount();
        Long fromAccountId = fromAccount.getId();
        Long toAccountId = toAccount.getId();
        Long toAmountMinor = targetAmountOrNull(expense, income);
        String description = expense.getDescription();
        return new TransactionCommand(TransactionType.TRANSFER, expense.getBookedOn(),
            expense.getAmountMinor(), expense.getCurrency(), null, null, fromAccountId,
            toAccountId, toAmountMinor, null, description, null);
    }

    /**
     * Kwota po stronie docelowej jest potrzebna tylko wtedy, gdy konta mają
     * różne waluty — przy tej samej walucie podana wprost byłaby powtórzeniem.
     */
    private Long targetAmountOrNull(Transaction expense, Transaction income) {

        Account fromAccount = expense.getAccount();
        Account toAccount = income.getAccount();
        String fromCurrency = fromAccount.getCurrency();
        String toCurrency = toAccount.getCurrency();
        if (Objects.equals(fromCurrency, toCurrency)) {

            return null;
        }
        return income.getAmountMinor();
    }

    private void pairWith(Transaction expense, List<Transaction> incomes, Set<Long> usedIncomes,
                          List<TransferCandidate> candidates) {

        incomes.stream()
            .filter(income -> !usedIncomes.contains(income.getId()))
            .filter(income -> looksLikeTransfer(expense, income))
            .findFirst()
            .ifPresent(income -> accept(expense, income, usedIncomes, candidates));
    }

    private void accept(Transaction expense, Transaction income, Set<Long> usedIncomes,
                        List<TransferCandidate> candidates) {

        usedIncomes.add(income.getId());
        candidates.add(toCandidate(expense, income));
    }

    private TransferCandidate toCandidate(Transaction expense, Transaction income) {

        long daysApart = daysBetween(expense, income);
        Account fromAccount = expense.getAccount();
        Account toAccount = income.getAccount();
        return new TransferCandidate(
            expense.getId(), fromAccount.getId(), fromAccount.getName(), expense.getBookedOn(),
            income.getId(), toAccount.getId(), toAccount.getName(), income.getBookedOn(),
            expense.getAmountMinor(), expense.getCurrency(), daysApart);
    }

    private boolean looksLikeTransfer(Transaction expense, Transaction income) {

        Account fromAccount = expense.getAccount();
        Account toAccount = income.getAccount();
        boolean sameAccount = Objects.equals(fromAccount.getId(), toAccount.getId());
        if (sameAccount) {

            return false;
        }
        if (expense.getAmountMinor() != income.getAmountMinor()) {

            return false;
        }
        if (!Objects.equals(expense.getCurrency(), income.getCurrency())) {

            return false;
        }
        return daysBetween(expense, income) <= MAX_DAYS_APART;
    }

    private long daysBetween(Transaction expense, Transaction income) {

        LocalDate expenseOn = expense.getBookedOn();
        LocalDate incomeOn = income.getBookedOn();
        long days = ChronoUnit.DAYS.between(expenseOn, incomeOn);
        return Math.abs(days);
    }

    private boolean isExpense(Transaction transaction) {

        return Objects.equals(transaction.getType(), TransactionType.EXPENSE);
    }

    private boolean isIncome(Transaction transaction) {

        return Objects.equals(transaction.getType(), TransactionType.INCOME);
    }

    private void requirePair(Transaction expense, Transaction income) {

        boolean shaped = isExpense(expense) && isIncome(income) && looksLikeTransfer(expense,
            income);
        if (shaped) {

            return;
        }
        throw new ValidationException(ErrorCodes.TRANSFER_PAIR_MISMATCH,
            ExceptionMessageConstants.TRANSFER_PAIR_MISMATCH.formatted(
                expense.getId(), income.getId()));
    }

    /**
     * Propozycja scalenia. Nic tu nie jest jeszcze zapisane — to widok dwóch
     * istniejących transakcji obok siebie.
     */
    public record TransferCandidate(
        long expenseTransactionId,
        long fromAccountId,
        String fromAccountName,
        LocalDate expenseBookedOn,
        long incomeTransactionId,
        long toAccountId,
        String toAccountName,
        LocalDate incomeBookedOn,
        long amountMinor,
        String currency,
        long daysApart) {

    }
}
