package com.pgoogol.finance.imports.domain;

import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.imports.statement.RawRow;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
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

import java.time.LocalDate;
import java.util.Objects;

/**
 * Wiersz wyciągu zapisany w bazie — stan pośredni między plikiem a transakcją.
 *
 * <p>Kwota zostaje tu <b>ze znakiem</b>, dokładnie jak na wyciągu. Zamiana znaku
 * na typ transakcji odbywa się dopiero przy zatwierdzeniu, w jednym miejscu:
 * gdyby robił to parser, każdy bank miałby własną interpretację.</p>
 */
@Entity
@Table(name = "import_row")
public class ImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "booked_on", nullable = false)
    private LocalDate bookedOn;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "original_amount_minor")
    private Long originalAmountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "original_currency", length = 3)
    private String originalCurrency;

    @Column(length = 255)
    private String description;

    @Column(length = 255)
    private String counterparty;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportRowStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_category_id")
    private Category suggestedCategory;

    /**
     * Propozycja rozliczenia rachunku cyklicznego tym wierszem. Propozycja,
     * nie fakt — pozycja terminarza zmienia status dopiero po potwierdzeniu
     * przy zatwierdzaniu wyciągu.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_occurrence_id")
    private ScheduledOccurrence suggestedOccurrence;

    protected ImportRow() {

    }

    public ImportRow(ImportBatch batch, RawRow raw, String currency, String dedupKey,
                     ImportRowStatus status) {

        this.batch = Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(raw, "raw");
        this.ordinal = raw.ordinal();
        this.bookedOn = raw.bookedOn();
        this.amountMinor = raw.amountMinor();
        this.currency = Objects.requireNonNull(currency, "currency");
        this.originalAmountMinor = raw.originalAmountMinor();
        this.originalCurrency = raw.originalCurrency();
        this.description = raw.description();
        this.counterparty = raw.counterparty();
        this.bankReference = raw.bankReference();
        this.dedupKey = Objects.requireNonNull(dedupKey, "dedupKey");
        this.status = Objects.requireNonNull(status, "status");
    }

    public void markCommitted() {

        this.status = ImportRowStatus.COMMITTED;
    }

    public void suggestCategory(@Nullable Category category) {

        this.suggestedCategory = category;
    }

    public void suggestOccurrence(@Nullable ScheduledOccurrence occurrence) {

        this.suggestedOccurrence = occurrence;
    }

    @Nullable
    public ScheduledOccurrence getSuggestedOccurrence() {

        return suggestedOccurrence;
    }

    public boolean isDuplicate() {

        return Objects.equals(status, ImportRowStatus.DUPLICATE);
    }

    /** Wydatek czy wpływ — decyduje znak kwoty z wyciągu. */
    public boolean isOutgoing() {

        return amountMinor < 0;
    }

    /** Kwota bez znaku; transakcja trzyma zawsze wartość dodatnią. */
    public long absoluteAmountMinor() {

        return Math.abs(amountMinor);
    }

    public Long getId() {

        return id;
    }

    public ImportBatch getBatch() {

        return batch;
    }

    public int getOrdinal() {

        return ordinal;
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

    @Nullable
    public Long getOriginalAmountMinor() {

        return originalAmountMinor;
    }

    @Nullable
    public String getOriginalCurrency() {

        return originalCurrency;
    }

    @Nullable
    public String getDescription() {

        return description;
    }

    @Nullable
    public String getCounterparty() {

        return counterparty;
    }

    @Nullable
    public String getBankReference() {

        return bankReference;
    }

    public String getDedupKey() {

        return dedupKey;
    }

    public ImportRowStatus getStatus() {

        return status;
    }

    @Nullable
    public Category getSuggestedCategory() {

        return suggestedCategory;
    }
}
