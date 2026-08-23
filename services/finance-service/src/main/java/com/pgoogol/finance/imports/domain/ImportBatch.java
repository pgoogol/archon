package com.pgoogol.finance.imports.domain;

import com.pgoogol.finance.account.domain.Account;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Jedno wgranie wyciągu. Trzyma skrót pliku, więc ten sam eksport wgrany drugi
 * raz odpada na unikalności, zanim ktokolwiek go sparsuje.
 *
 * <p>Salda z nagłówka i stopki są zapisane wyłącznie po to, żeby po zatwierdzeniu
 * dało się porównać je z saldem wyliczonym z transakcji. Nigdy nie służą do
 * poprawiania danych.</p>
 */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    // char(64) w bazie, nie varchar — bez tej adnotacji Hibernate przy
    // ddl-auto: validate widzi bpchar i wywala start aplikacji
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportBatchStatus status;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "opening_balance_minor")
    private Long openingBalanceMinor;

    @Column(name = "closing_balance_minor")
    private Long closingBalanceMinor;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    protected ImportBatch() {

    }

    public ImportBatch(Account account, String fileName, String fileHash) {

        this.account = Objects.requireNonNull(account, "account");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.fileHash = Objects.requireNonNull(fileHash, "fileHash");
        this.status = ImportBatchStatus.PARSED;
        this.uploadedAt = Instant.now();
    }

    /** Metryki wyciągu przepisane z nagłówka i stopki pliku. */
    public void describeStatement(@Nullable LocalDate periodFrom, @Nullable LocalDate periodTo,
                                  @Nullable Long openingBalanceMinor,
                                  @Nullable Long closingBalanceMinor, int rowCount) {

        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.openingBalanceMinor = openingBalanceMinor;
        this.closingBalanceMinor = closingBalanceMinor;
        this.rowCount = rowCount;
    }

    public void markCommitted() {

        this.status = ImportBatchStatus.COMMITTED;
        this.committedAt = Instant.now();
    }

    public boolean isCommitted() {

        return Objects.equals(status, ImportBatchStatus.COMMITTED);
    }

    public Long getId() {

        return id;
    }

    public Account getAccount() {

        return account;
    }

    public String getFileName() {

        return fileName;
    }

    public String getFileHash() {

        return fileHash;
    }

    public ImportBatchStatus getStatus() {

        return status;
    }

    @Nullable
    public LocalDate getPeriodFrom() {

        return periodFrom;
    }

    @Nullable
    public LocalDate getPeriodTo() {

        return periodTo;
    }

    @Nullable
    public Long getOpeningBalanceMinor() {

        return openingBalanceMinor;
    }

    @Nullable
    public Long getClosingBalanceMinor() {

        return closingBalanceMinor;
    }

    public int getRowCount() {

        return rowCount;
    }

    public Instant getUploadedAt() {

        return uploadedAt;
    }

    @Nullable
    public Instant getCommittedAt() {

        return committedAt;
    }
}
