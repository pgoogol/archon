package com.pgoogol.kitchen.recipe.domain;

import com.pgoogol.kitchen.dictionary.domain.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Krok przygotowania. Czas i temperatura są wyciągnięte do pól, ale tekst kroku
 * zostaje pełny — „piecz 40 minut w 180°C" ma się dać przeczytać w całości.
 *
 * <p>Powiązanie ze składnikami trzyma tabela pośrednia, a nie tekst kroku:
 * dzięki temu ekran podświetla „w tym kroku użyjesz…", a przyszłe skalowanie
 * porcji nie musi parsować zdań.</p>
 */
@Entity
@Table(name = "recipe_step")
public class RecipeStep {

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

    @Column(nullable = false)
    private String text;

    @Column(name = "source_text")
    private String sourceText;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "temperature_c")
    private Integer temperatureC;

    @Column(name = "temperature_note", length = 100)
    private String temperatureNote;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_step_ingredient",
        joinColumns = @JoinColumn(name = "step_id"),
        inverseJoinColumns = @JoinColumn(name = "recipe_ingredient_id"))
    private Set<RecipeIngredient> ingredients = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_step_equipment",
        joinColumns = @JoinColumn(name = "step_id"),
        inverseJoinColumns = @JoinColumn(name = "equipment_id"))
    private Set<Equipment> equipment = new LinkedHashSet<>();

    protected RecipeStep() {

    }

    public RecipeStep(Recipe recipe, int position, String text) {

        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.position = position;
        this.text = Objects.requireNonNull(text, "text");
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

    public String getGroupLabel() {

        return groupLabel;
    }

    public void setGroupLabel(String groupLabel) {

        this.groupLabel = groupLabel;
    }

    public String getText() {

        return text;
    }

    public void setText(String text) {

        this.text = text;
    }

    public String getSourceText() {

        return sourceText;
    }

    public void setSourceText(String sourceText) {

        this.sourceText = sourceText;
    }

    public Integer getDurationMinutes() {

        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {

        this.durationMinutes = durationMinutes;
    }

    public Integer getTemperatureC() {

        return temperatureC;
    }

    public void setTemperatureC(Integer temperatureC) {

        this.temperatureC = temperatureC;
    }

    public String getTemperatureNote() {

        return temperatureNote;
    }

    public void setTemperatureNote(String temperatureNote) {

        this.temperatureNote = temperatureNote;
    }

    public Set<RecipeIngredient> getIngredients() {

        return ingredients;
    }

    public Set<Equipment> getEquipment() {

        return equipment;
    }
}
