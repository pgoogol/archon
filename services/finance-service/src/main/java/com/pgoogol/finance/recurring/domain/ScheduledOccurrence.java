package com.pgoogol.finance.recurring.domain;

import com.pgoogol.finance.transaction.domain.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Jedna pozycja terminarza: konkretny termin wynikający z reguły.
 *
 * <p>Statusem przechowywanym jest wyłącznie {@code PENDING}, {@code PAID}
 * i {@code SKIPPED}. {@link OccurrenceStatus#OVERDUE} wylicza
 * {@link #statusOn(LocalDate)} przy odczycie — przechowywany wymagałby joba
 * przepisującego statusy o północy.</p>
 */
@Entity
@Table(name = "scheduled_occurrence")
public class ScheduledOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private RecurringRule rule;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "expected_amount_minor", nullable = false)
    private long expectedAmountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OccurrenceStatus status;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "paid_amount_minor")
    private Long paidAmountMinor;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    protected ScheduledOccurrence() {

    }

    public ScheduledOccurrence(RecurringRule rule, LocalDate dueDate, long expectedAmountMinor,
                               String currency) {

        this.rule = Objects.requireNonNull(rule, "rule");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.expectedAmountMinor = expectedAmountMinor;
        this.status = OccurrenceStatus.PENDING;
    }

    /**
     * Dostrojenie oczekiwanej kwoty i terminu po zmianie reguły. Wolno wyłącznie
     * na pozycji czekającej — historii nie przepisujemy.
     */
    public void expect(long expectedAmountMinor, String currency) {

        this.expectedAmountMinor = expectedAmountMinor;
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public void markPaid(LocalDate paidOn, long paidAmountMinor, Transaction transaction) {

        this.paidOn = Objects.requireNonNull(paidOn, "paidOn");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.paidAmountMinor = paidAmountMinor;
        this.status = OccurrenceStatus.PAID;
    }

    public void markSkipped() {

        this.status = OccurrenceStatus.SKIPPED;
    }

    public boolean isPending() {

        return Objects.equals(status, OccurrenceStatus.PENDING);
    }

    /**
     * Status widziany danego dnia. Jedyne miejsce, w którym powstaje
     * {@code OVERDUE}.
     */
    public OccurrenceStatus statusOn(LocalDate today) {

        Objects.requireNonNull(today, "today");
        if (!isPending()) {

            return status;
        }
        if (dueDate.isBefore(today)) {

            return OccurrenceStatus.OVERDUE;
        }
        return OccurrenceStatus.PENDING;
    }

    public Long getId() {

        return id;
    }

    public RecurringRule getRule() {

        return rule;
    }

    public LocalDate getDueDate() {

        return dueDate;
    }

    public long getExpectedAmountMinor() {

        return expectedAmountMinor;
    }

    public String getCurrency() {

        return currency;
    }

    public OccurrenceStatus getStatus() {

        return status;
    }

    @Nullable
    public LocalDate getPaidOn() {

        return paidOn;
    }

    @Nullable
    public Long getPaidAmountMinor() {

        return paidAmountMinor;
    }

    @Nullable
    public Transaction getTransaction() {

        return transaction;
    }
}
