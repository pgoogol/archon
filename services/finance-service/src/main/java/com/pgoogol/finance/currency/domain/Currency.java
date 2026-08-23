package com.pgoogol.finance.currency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/**
 * Waluta słownikowa. {@code minorUnit} jest tu, a nie w kodzie, bo liczba miejsc
 * po przecinku różni się między walutami i zmiana wymagałaby inaczej migracji
 * wszystkich kwot.
 */
@Entity
@Table(name = "currency")
public class Currency {

    @Id
    // kolumna jest char(3), nie varchar — bez jawnego typu walidacja schematu
    // przy starcie zgłasza rozjazd bpchar kontra varchar
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "minor_unit", nullable = false)
    private short minorUnit;

    protected Currency() {

    }

    public Currency(String code, String name, int minorUnit) {

        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        MinorUnits units = new MinorUnits(minorUnit);
        this.minorUnit = (short) units.scale();
    }

    public String getCode() {

        return code;
    }

    public String getName() {

        return name;
    }

    public int getMinorUnit() {

        return minorUnit;
    }

    public MinorUnits minorUnits() {

        return new MinorUnits(minorUnit);
    }

    public void rename(String name) {

        this.name = Objects.requireNonNull(name, "name");
    }
}
