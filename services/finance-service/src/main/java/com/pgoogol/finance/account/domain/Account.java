package com.pgoogol.finance.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Konto bankowe, gotówkowe albo karta. Saldo NIE jest tu przechowywane — liczy
 * się je zapytaniem z salda otwarcia i transakcji, więc nie ma jak rozjechać się
 * z historią.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 34)
    private String iban;

    @Column(name = "opening_balance_minor", nullable = false)
    private long openingBalanceMinor;

    @Column(name = "opening_balance_on", nullable = false)
    private LocalDate openingBalanceOn;

    @Column(nullable = false)
    private boolean archived;

    protected Account() {

    }

    public Account(String name, AccountType type, String currency, @Nullable String iban,
                   long openingBalanceMinor, LocalDate openingBalanceOn) {

        this.currency = Objects.requireNonNull(currency, "currency");
        rename(name, type, iban, openingBalanceMinor, openingBalanceOn);
    }

    public void rename(String name, AccountType type, @Nullable String iban,
                       long openingBalanceMinor, LocalDate openingBalanceOn) {

        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.iban = iban;
        this.openingBalanceMinor = openingBalanceMinor;
        this.openingBalanceOn = Objects.requireNonNull(openingBalanceOn, "openingBalanceOn");
    }

    /** Archiwizacja zamiast usunięcia — historia transakcji musi zostać. */
    public void archive() {
        this.archived = true;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    @Nullable
    public String getIban() {
        return iban;
    }

    public long getOpeningBalanceMinor() {
        return openingBalanceMinor;
    }

    public LocalDate getOpeningBalanceOn() {
        return openingBalanceOn;
    }

    public boolean isArchived() {
        return archived;
    }
}
