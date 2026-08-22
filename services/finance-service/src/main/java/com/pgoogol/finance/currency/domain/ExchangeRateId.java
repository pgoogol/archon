package com.pgoogol.finance.currency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Klucz kursu: waluta plus data. Klasa mutowalna, a nie rekord — Hibernate
 * wymaga konstruktora bezargumentowego dla klucza złożonego.
 */
@Embeddable
public class ExchangeRateId implements Serializable {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", length = 3)
    private String code;

    @Column(name = "rate_date")
    private LocalDate rateDate;

    protected ExchangeRateId() {

    }

    public ExchangeRateId(String code, LocalDate rateDate) {

        this.code = Objects.requireNonNull(code, "code");
        this.rateDate = Objects.requireNonNull(rateDate, "rateDate");
    }

    public String getCode() {

        return code;
    }

    public LocalDate getRateDate() {

        return rateDate;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {

            return true;
        }
        if (!(other instanceof ExchangeRateId that)) {

            return false;
        }
        return Objects.equals(code, that.code) && Objects.equals(rateDate, that.rateDate);
    }

    @Override
    public int hashCode() {

        return Objects.hash(code, rateDate);
    }
}
