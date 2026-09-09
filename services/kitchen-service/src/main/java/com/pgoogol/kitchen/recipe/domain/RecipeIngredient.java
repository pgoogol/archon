package com.pgoogol.kitchen.recipe.domain;

import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Jeden składnik jednego przepisu.
 *
 * <p>{@code displayName} to nazwa z tego przepisu („czerwona cebula"),
 * {@code ingredient} to pozycja katalogu — pusta, dopóki nic nie dopasowano.
 * Ilość jest zakresem: przy jednej wartości oba końce są równe, a przy
 * „szczypcie" i „do smaku" oba są puste i mówi o tym {@code quantityText}.</p>
 *
 * <p>Identyfikator wiersza przeżywa edycje — dziennik zmian rozpoznaje po nim,
 * że to ten sam składnik z inną ilością.</p>
 */
@Entity
@Table(name = "recipe_ingredient")
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false)
    private int position;

    @Column(name = "group_label", length = 100)
    private String groupLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "source_text")
    private String sourceText;

    @Column(name = "quantity_min")
    private BigDecimal quantityMin;

    @Column(name = "quantity_max")
    private BigDecimal quantityMax;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "quantity_text", length = 100)
    private String quantityText;

    @Column(length = 200)
    private String preparation;

    @Column(nullable = false)
    private boolean optional;

    @Column
    private String note;

    @OneToMany(mappedBy = "recipeIngredient", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RecipeIngredientAlternative> alternatives = new ArrayList<>();

    protected RecipeIngredient() {

    }

    public RecipeIngredient(Recipe recipe, int position, String displayName) {

        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.position = position;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    public RecipeIngredientAlternative addAlternative(int position, String displayName) {

        RecipeIngredientAlternative alternative =
            new RecipeIngredientAlternative(this, position, displayName);
        alternatives.add(alternative);
        return alternative;
    }

    public void clearAlternatives() {

        alternatives.clear();
    }

    public Long getId() {

        return id;
    }

    public Recipe getRecipe() {

        return recipe;
    }

    public int getPosition() {

        return position;
    }

    public void setPosition(int position) {

        this.position = position;
    }

    public String getGroupLabel() {

        return groupLabel;
    }

    public void setGroupLabel(String groupLabel) {

        this.groupLabel = groupLabel;
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

    public String getSourceText() {

        return sourceText;
    }

    public void setSourceText(String sourceText) {

        this.sourceText = sourceText;
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

    public String getPreparation() {

        return preparation;
    }

    public void setPreparation(String preparation) {

        this.preparation = preparation;
    }

    public boolean isOptional() {

        return optional;
    }

    public void setOptional(boolean optional) {

        this.optional = optional;
    }

    public String getNote() {

        return note;
    }

    public void setNote(String note) {

        this.note = note;
    }

    public List<RecipeIngredientAlternative> getAlternatives() {

        return alternatives;
    }
}
