package com.pgoogol.finance.currency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Kurs waluty na dany dzień: ile waluty bazowej za jednostkę tej waluty.
 * Zapisany lokalnie, żeby historia nie zależała od dostępności zewnętrznego API.
 */
@Entity
@Table(name = "exchange_rate")
public class ExchangeRate {

    @EmbeddedId
    private ExchangeRateId id;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RateSource source;

    protected ExchangeRate() {

    }

    public ExchangeRate(String code, LocalDate rateDate, BigDecimal rate, RateSource source) {

        this.id = new ExchangeRateId(code, rateDate);
        this.source = Objects.requireNonNull(source, "source");
        replaceRate(rate);
    }

    public ExchangeRateId getId() {

        return id;
    }

    public String getCode() {

        return id.getCode();
    }

    public LocalDate getRateDate() {

        return id.getRateDate();
    }

    public BigDecimal getRate() {

        return rate;
    }

    public RateSource getSource() {

        return source;
    }

    public FxRate toFxRate() {

        return new FxRate(rate, id.getRateDate());
    }

    public void replaceRate(BigDecimal rate, RateSource source) {

        replaceRate(rate);
        this.source = Objects.requireNonNull(source, "source");
    }

    private void replaceRate(BigDecimal rate) {

        Objects.requireNonNull(rate, "rate");
        if (rate.signum() <= 0) {

            throw new IllegalArgumentException("Kurs musi być dodatni: " + rate);
        }
        this.rate = rate;
    }
}
