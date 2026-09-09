package com.pgoogol.kitchen.ingredient.domain;

import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;
import com.pgoogol.kitchen.dictionary.domain.Unit;
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

import java.time.Instant;
import java.util.Objects;

/**
 * Pozycja katalogu składników — kanoniczna nazwa produktu w liczbie pojedynczej
 * i mianowniku („cebula", nie „cebule").
 *
 * <p>To po niej działa wyszukiwanie „mam kurczaka i cukinię", a w przyszłości
 * lista zakupów i spiżarnia. Warianty i literówki prowadzą tu przez aliasy.</p>
 */
@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 200)
    private String nameNormalized;

    @Column(length = 50)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_unit_id")
    private Unit defaultUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngredientStatus status = IngredientStatus.NEW;

    /** Wypełnione po scaleniu duplikatu — stare odwołania mają dokąd prowadzić. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private Ingredient mergedInto;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Ingredient() {

    }

    public Ingredient(String name, IngredientStatus status) {

        rename(name);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Instant.now();
    }

    public final void rename(String name) {

        this.name = Objects.requireNonNull(name, "name").trim();
        this.nameNormalized = NameNormalizer.normalize(this.name);
    }

    public void mergeInto(Ingredient target) {

        this.mergedInto = Objects.requireNonNull(target, "target");
    }

    public Long getId() {

        return id;
    }

    public String getName() {

        return name;
    }

    public String getNameNormalized() {

        return nameNormalized;
    }

    public String getCategory() {

        return category;
    }

    public void setCategory(String category) {

        this.category = category;
    }

    public Unit getDefaultUnit() {

        return defaultUnit;
    }

    public void setDefaultUnit(Unit defaultUnit) {

        this.defaultUnit = defaultUnit;
    }

    public IngredientStatus getStatus() {

        return status;
    }

    public void setStatus(IngredientStatus status) {

        this.status = status;
    }

    public Ingredient getMergedInto() {

        return mergedInto;
    }

    public Instant getCreatedAt() {

        return createdAt;
    }
}
