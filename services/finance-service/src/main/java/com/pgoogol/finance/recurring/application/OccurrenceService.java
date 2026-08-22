package com.pgoogol.finance.recurring.application;

import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.recurring.domain.OccurrenceStatus;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import com.pgoogol.finance.recurring.infrastructure.ScheduledOccurrenceRepository;
import com.pgoogol.finance.transaction.application.TransactionCommand;
import com.pgoogol.finance.transaction.application.TransactionService;
import com.pgoogol.finance.transaction.domain.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Terminarz płatności: odczyt i rozliczanie pozycji.
 *
 * <p>Zapłacenie pozycji zakłada transakcję na <b>faktyczną</b> kwotę i datę,
 * nie na oczekiwaną. Rachunek za prąd rzadko wychodzi co do grosza tak samo,
 * a saldo ma się zgadzać z wyciągiem, nie z planem.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OccurrenceService {

    private final ScheduledOccurrenceRepository occurrenceRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;

    /**
     * @param status filtr, w którym {@code OVERDUE} znaczy „czekająca po
     *               terminie" — w bazie takiego statusu nie ma
     */
    public List<ScheduledOccurrence> search(@Nullable LocalDate from, @Nullable LocalDate to,
                                            @Nullable OccurrenceStatus status,
                                            @Nullable Long ruleId) {

        LocalDate today = LocalDate.now();
        return occurrenceRepository.search(from, to, status, ruleId, today);
    }

    public ScheduledOccurrence get(long id) {

        Optional<ScheduledOccurrence> occurrence = occurrenceRepository.findDetailedById(id);
        return occurrence.orElseThrow(() -> new NotFoundException(ErrorCodes.OCCURRENCE_NOT_FOUND,
            ExceptionMessageConstants.OCCURRENCE_NOT_FOUND.formatted(id)));
    }

    @Transactional
    public ScheduledOccurrence pay(long id, LocalDate paidOn, long paidAmountMinor,
                                   @Nullable Long accountId) {

        ScheduledOccurrence occurrence = get(id);
        requirePending(occurrence);
        RecurringRule rule = occurrence.getRule();
        Account account = paymentAccount(rule, accountId);
        requireMatchingCurrency(occurrence, account);

        TransactionCommand command = paymentCommand(occurrence, rule, account, paidOn,
            paidAmountMinor);
        Transaction transaction = transactionService.create(command);
        occurrence.markPaid(paidOn, paidAmountMinor, transaction);
        return occurrence;
    }

    /**
     * Rozliczenie pozycji transakcją, która już powstała — tak wchodzi
     * potwierdzona płatność z importu wyciągu. Osobno od {@link #pay}, bo tam
     * transakcję zakłada dopiero ten serwis, a tu istnieje wcześniej.
     */
    @Transactional
    public ScheduledOccurrence settleWith(long id, LocalDate paidOn, long paidAmountMinor,
                                          Transaction transaction) {

        Objects.requireNonNull(transaction, "transaction");
        ScheduledOccurrence occurrence = get(id);
        requirePending(occurrence);
        occurrence.markPaid(paidOn, paidAmountMinor, transaction);
        return occurrence;
    }

    @Transactional
    public ScheduledOccurrence skip(long id) {

        ScheduledOccurrence occurrence = get(id);
        requirePending(occurrence);
        occurrence.markSkipped();
        return occurrence;
    }

    private TransactionCommand paymentCommand(ScheduledOccurrence occurrence, RecurringRule rule,
                                              Account account, LocalDate paidOn,
                                              long paidAmountMinor) {

        Long categoryId = rule.getCategory().getId();
        Long accountId = account.getId();
        String currency = account.getCurrency();
        String description = rule.getName();
        return new TransactionCommand(rule.getType(), paidOn, paidAmountMinor, currency,
            null, null, accountId, null, null, categoryId, description, null);
    }

    private Account paymentAccount(RecurringRule rule, @Nullable Long accountId) {

        if (Objects.isNull(accountId)) {

            return rule.getAccount();
        }
        return accountService.get(accountId);
    }

    private void requirePending(ScheduledOccurrence occurrence) {

        if (occurrence.isPending()) {

            return;
        }
        // pozycja rozliczona drugi raz założyłaby drugą transakcję na ten sam
        // rachunek — z konfliktem, nie z cichym nadpisaniem
        throw new ConflictException(ErrorCodes.OCCURRENCE_ALREADY_SETTLED,
            ExceptionMessageConstants.OCCURRENCE_ALREADY_SETTLED.formatted(
                occurrence.getId(), occurrence.getStatus()));
    }

    private void requireMatchingCurrency(ScheduledOccurrence occurrence, Account account) {

        String accountCurrency = account.getCurrency();
        String expected = occurrence.getCurrency();
        if (Objects.equals(accountCurrency, expected)) {

            return;
        }
        throw new ValidationException(ErrorCodes.CURRENCY_MISMATCH,
            ExceptionMessageConstants.OCCURRENCE_PAID_FROM_OTHER_CURRENCY.formatted(
                accountCurrency, expected));
    }
}
