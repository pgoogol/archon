package com.pgoogol.finance.transaction.domain;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.currency.domain.FxRate;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Pojedyncza operacja finansowa.
 *
 * <p>Kwota jest <b>zawsze dodatnia</b> — kierunek wynika z {@link TransactionType}.
 * Kurs użyty do przeliczenia na walutę bazową jest zapisany na transakcji razem
 * z datą, z której pochodzi. Gdyby raport przeliczał kwoty kursem bieżącym,
 * zeszłoroczne podsumowanie zmieniałoby się każdego dnia.</p>
 */
@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "booked_on", nullable = false)
    private LocalDate bookedOn;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "base_amount_minor", nullable = false)
    private long baseAmountMinor;

    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "fx_rate_date", nullable = false)
    private LocalDate fxRateDate;

    @Column(name = "original_amount_minor")
    private Long originalAmountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "original_currency", length = 3)
    private String originalCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(name = "to_amount_minor")
    private Long toAmountMinor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 255)
    private String description;

    @Column(length = 255)
    private String counterparty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transaction() {

    }

    public Transaction(TransactionType type, LocalDate bookedOn, long amountMinor, String currency,
                       Account account) {

        this.type = Objects.requireNonNull(type, "type");
        this.bookedOn = Objects.requireNonNull(bookedOn, "bookedOn");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.account = Objects.requireNonNull(account, "account");
        this.createdAt = Instant.now();
        setAmountMinor(amountMinor);
    }

    /**
     * Kwota bazowa i kurs zawsze zmieniają się razem — osobne settery pozwoliłyby
     * zapisać kwotę policzoną innym kursem, niż mówi zapisana wartość.
     */
    public void applyBaseAmount(long baseAmountMinor, FxRate rate) {

        Objects.requireNonNull(rate, "rate");
        this.baseAmountMinor = baseAmountMinor;
        this.fxRate = rate.rate();
        this.fxRateDate = rate.rateDate();
    }

    public void describe(@Nullable String description, @Nullable String counterparty) {

        this.description = description;
        this.counterparty = counterparty;
    }

    public void assignCategory(@Nullable Category category) {

        this.category = category;
    }

    public void assignTransferTarget(@Nullable Account toAccount, @Nullable Long toAmountMinor) {

        this.toAccount = toAccount;
        this.toAmountMinor = toAmountMinor;
    }

    public void assignOriginal(@Nullable Long originalAmountMinor,
                               @Nullable String originalCurrency) {

        this.originalAmountMinor = originalAmountMinor;
        this.originalCurrency = originalCurrency;
    }

    public void rebook(TransactionType type, LocalDate bookedOn, long amountMinor, String currency,
                       Account account) {

        this.type = Objects.requireNonNull(type, "type");
        this.bookedOn = Objects.requireNonNull(bookedOn, "bookedOn");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.account = Objects.requireNonNull(account, "account");
        setAmountMinor(amountMinor);
    }

    private void setAmountMinor(long amountMinor) {

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Kwota musi być dodatnia: " + amountMinor);
        }
        this.amountMinor = amountMinor;
    }

    public Long getId() {

        return id;
    }

    public TransactionType getType() {

        return type;
    }

    public LocalDate getBookedOn() {

        return bookedOn;
    }

    public long getAmountMinor() {

        return amountMinor;
    }

    public String getCurrency() {

        return currency;
    }

    public long getBaseAmountMinor() {

        return baseAmountMinor;
    }

    public BigDecimal getFxRate() {

        return fxRate;
    }

    public LocalDate getFxRateDate() {

        return fxRateDate;
    }

    @Nullable
    public Long getOriginalAmountMinor() {

        return originalAmountMinor;
    }

    @Nullable
    public String getOriginalCurrency() {

        return originalCurrency;
    }

    public Account getAccount() {

        return account;
    }

    @Nullable
    public Account getToAccount() {

        return toAccount;
    }

    @Nullable
    public Long getToAmountMinor() {

        return toAmountMinor;
    }

    @Nullable
    public Category getCategory() {

        return category;
    }

    @Nullable
    public String getDescription() {

        return description;
    }

    @Nullable
    public String getCounterparty() {

        return counterparty;
    }

    public Instant getCreatedAt() {

        return createdAt;
    }
}
