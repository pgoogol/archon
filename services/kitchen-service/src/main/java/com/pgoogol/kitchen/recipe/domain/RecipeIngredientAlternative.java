package com.pgoogol.kitchen.recipe.domain;

import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Zamiennik składnika: „masło albo margaryna", „mleko albo napój owsiany".
 * Osobny wiersz, bo ma własną ilość i własną jednostkę.
 */
@Entity
@Table(name = "recipe_ingredient_alternative")
public class RecipeIngredientAlternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_ingredient_id", nullable = false)
    private RecipeIngredient recipeIngredient;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "quantity_min")
    private BigDecimal quantityMin;

    @Column(name = "quantity_max")
    private BigDecimal quantityMax;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "quantity_text", length = 100)
    private String quantityText;

    @Column
    private String note;

    protected RecipeIngredientAlternative() {

    }

    RecipeIngredientAlternative(RecipeIngredient recipeIngredient, int position, String displayName) {

        this.recipeIngredient = Objects.requireNonNull(recipeIngredient, "recipeIngredient");
        this.position = position;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    public Long getId() {

        return id;
    }

    public int getPosition() {

        return position;
    }

    public void setPosition(int position) {

        this.position = position;
    }

    public Ingredient getIngredient() {

        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {

        this.ingredient = ingredient;
    }

    public String getDisplayName() {

        return displayName;
    }

    public void setDisplayName(String displayName) {

        this.displayName = displayName;
    }

    public BigDecimal getQuantityMin() {

        return quantityMin;
    }

    public void setQuantityMin(BigDecimal quantityMin) {

        this.quantityMin = quantityMin;
    }

    public BigDecimal getQuantityMax() {

        return quantityMax;
    }

    public void setQuantityMax(BigDecimal quantityMax) {

        this.quantityMax = quantityMax;
    }

    public Unit getUnit() {

        return unit;
    }

    public void setUnit(Unit unit) {

        this.unit = unit;
    }

    public String getQuantityText() {

        return quantityText;
    }

    public void setQuantityText(String quantityText) {

        this.quantityText = quantityText;
    }

    public String getNote() {

        return note;
    }

    public void setNote(String note) {

        this.note = note;
    }
}
