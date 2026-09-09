package com.pgoogol.kitchen.ingredient.domain;

import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Synonim prowadzący do pozycji katalogu: „cebulka", „onion", „cebula czerwona".
 * Unikalny globalnie — alias ma wskazywać jednoznacznie, inaczej dopasowanie
 * przy imporcie byłoby losowaniem.
 */
@Entity
@Table(name = "ingredient_alias")
public class IngredientAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, length = 200)
    private String alias;

    @Column(name = "alias_normalized", nullable = false, length = 200)
    private String aliasNormalized;

    @Column(length = 5)
    private String language;

    protected IngredientAlias() {

    }

    public IngredientAlias(Ingredient ingredient, String alias, String language) {

        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        this.alias = Objects.requireNonNull(alias, "alias").trim();
        this.aliasNormalized = NameNormalizer.normalize(this.alias);
        this.language = language;
    }

    public Long getId() {

        return id;
    }

    public Ingredient getIngredient() {

        return ingredient;
    }

    public String getAlias() {

        return alias;
    }

    public String getAliasNormalized() {

        return aliasNormalized;
    }

    public String getLanguage() {

        return language;
    }
}
