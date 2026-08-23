package com.pgoogol.finance.recurring.domain;

import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.transaction.domain.TransactionType;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Reguła rachunku cyklicznego: co, z jakiego konta i jak często.
 *
 * <p>Kwota jest <b>oczekiwana</b>, nie faktyczna — rachunek za prąd rzadko
 * wychodzi co do grosza tak samo, więc kwota rzeczywista trafia dopiero na
 * pozycję terminarza przy płatności.</p>
 */
@Entity
@Table(name = "recurring_rule")
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecurringFrequency frequency;

    @Column(name = "day_of_month", nullable = false)
    private short dayOfMonth;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "match_pattern", length = 255)
    private String matchPattern;

    @Column(nullable = false)
    private boolean active;

    /** Reguła bywa edytowana i generowana w tle jednocześnie. */
    @Version
    private Long version;

    protected RecurringRule() {

    }

    public RecurringRule(String name, Account account, Category category, TransactionType type,
                         long amountMinor, String currency, RecurringFrequency frequency,
                         int dayOfMonth, LocalDate startsOn) {

        this.name = Objects.requireNonNull(name, "name");
        this.account = Objects.requireNonNull(account, "account");
        this.category = Objects.requireNonNull(category, "category");
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.frequency = Objects.requireNonNull(frequency, "frequency");
        this.startsOn = Objects.requireNonNull(startsOn, "startsOn");
        this.dayOfMonth = (short) dayOfMonth;
        this.amountMinor = amountMinor;
        this.active = true;
    }

    /**
     * Konto przychodzi razem z walutą, bo to ono ją narzuca — reguła w walucie,
     * której konto nie prowadzi, opisywałaby rachunek nie do zapłacenia.
     */
    public void redefine(String name, Account account, Category category, TransactionType type,
                         long amountMinor, String currency, RecurringFrequency frequency,
                         int dayOfMonth, LocalDate startsOn, @Nullable LocalDate endsOn,
                         @Nullable String matchPattern) {

        this.name = Objects.requireNonNull(name, "name");
        this.account = Objects.requireNonNull(account, "account");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.category = Objects.requireNonNull(category, "category");
        this.type = Objects.requireNonNull(type, "type");
        this.frequency = Objects.requireNonNull(frequency, "frequency");
        this.startsOn = Objects.requireNonNull(startsOn, "startsOn");
        this.amountMinor = amountMinor;
        this.dayOfMonth = (short) dayOfMonth;
        this.endsOn = endsOn;
        this.matchPattern = matchPattern;
    }

    public void deactivate() {

        this.active = false;
    }

    public void describeMatching(@Nullable String matchPattern) {

        this.matchPattern = matchPattern;
    }

    public void limitTo(@Nullable LocalDate endsOn) {

        this.endsOn = endsOn;
    }

    public Long getId() {

        return id;
    }

    public String getName() {

        return name;
    }

    public Account getAccount() {

        return account;
    }

    public Category getCategory() {

        return category;
    }

    public TransactionType getType() {

        return type;
    }

    public long getAmountMinor() {

        return amountMinor;
    }

    public String getCurrency() {

        return currency;
    }

    public RecurringFrequency getFrequency() {

        return frequency;
    }

    public int getDayOfMonth() {

        return dayOfMonth;
    }

    public LocalDate getStartsOn() {

        return startsOn;
    }

    @Nullable
    public LocalDate getEndsOn() {

        return endsOn;
    }

    @Nullable
    public String getMatchPattern() {

        return matchPattern;
    }

    public boolean isActive() {

        return active;
    }
}
