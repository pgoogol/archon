package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Jednostka miary ze słownika.
 *
 * <p>{@code toBase} przelicza w obrębie rodzaju — gram dla masy, mililitr dla
 * objętości. Sztuki i szczypty go nie mają, bo nie sprowadzają się do wspólnej
 * miary. Przeliczenia między masą a objętością zależą od produktu (szklanka
 * mąki waży inaczej niż szklanka wody) i świadomie nie mieszkają w słowniku.</p>
 */
@Entity
@Table(name = "unit")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UnitKind kind;

    @Column(name = "to_base")
    private BigDecimal toBase;

    protected Unit() {

    }

    public Unit(String code, String name, UnitKind kind, BigDecimal toBase) {

        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.toBase = toBase;
    }

    public Long getId() {

        return id;
    }

    public String getCode() {

        return code;
    }

    public String getName() {

        return name;
    }

    public UnitKind getKind() {

        return kind;
    }

    public BigDecimal getToBase() {

        return toBase;
    }
}
