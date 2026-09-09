package com.pgoogol.kitchen.recipe.domain;

import com.pgoogol.kitchen.dictionary.domain.Cuisine;
import com.pgoogol.kitchen.dictionary.domain.Diet;
import com.pgoogol.kitchen.dictionary.domain.RecipeCategory;
import com.pgoogol.kitchen.dictionary.domain.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Przepis w stanie bieżącym. Historia zmian leży obok, w dzienniku rewizji —
 * tutaj jest wyłącznie to, co obowiązuje dzisiaj.
 *
 * <p>{@code currentRevisionNo} rośnie z każdą zapisaną zmianą i jest tym samym
 * numerem, który niesie rewizja. {@code lockVersion} pilnuje, żeby dwie
 * równoległe edycje nie nadpisały się po cichu.</p>
 */
@Entity
@Table(name = "recipe")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column
    private String description;

    @Column(name = "servings_amount")
    private BigDecimal servingsAmount;

    @Column(name = "servings_unit", length = 50)
    private String servingsUnit;

    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    @Column(name = "cook_minutes")
    private Integer cookMinutes;

    @Column(name = "total_minutes")
    private Integer totalMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuisine_id")
    private Cuisine cuisine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private RecipeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Difficulty difficulty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private RecipeSource source;

    @Column(name = "current_revision_no", nullable = false)
    private int currentRevisionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeStatus status = RecipeStatus.ACTIVE;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RecipeStep> steps = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_tag",
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_diet",
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "diet_id"))
    private Set<Diet> diets = new LinkedHashSet<>();

    protected Recipe() {

    }

    public Recipe(String title) {

        this.title = Objects.requireNonNull(title, "title");
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public RecipeIngredient addIngredient(int position, String displayName) {

        RecipeIngredient ingredient = new RecipeIngredient(this, position, displayName);
        ingredients.add(ingredient);
        return ingredient;
    }

    public RecipeStep addStep(int position, String text) {

        RecipeStep step = new RecipeStep(this, position, text);
        steps.add(step);
        return step;
    }

    /** Numer kolejnej rewizji; wywołujący zapisuje ją w tej samej transakcji. */
    public int nextRevisionNo() {

        currentRevisionNo = currentRevisionNo + 1;
        updatedAt = Instant.now();
        return currentRevisionNo;
    }

    public void archive() {

        status = RecipeStatus.ARCHIVED;
        updatedAt = Instant.now();
    }

    public Long getId() {

        return id;
    }

    public String getTitle() {

        return title;
    }

    public void setTitle(String title) {

        this.title = title;
    }

    public String getDescription() {

        return description;
    }

    public void setDescription(String description) {

        this.description = description;
    }

    public BigDecimal getServingsAmount() {

        return servingsAmount;
    }

    public void setServingsAmount(BigDecimal servingsAmount) {

        this.servingsAmount = servingsAmount;
    }

    public String getServingsUnit() {

        return servingsUnit;
    }

    public void setServingsUnit(String servingsUnit) {

        this.servingsUnit = servingsUnit;
    }

    public Integer getPrepMinutes() {

        return prepMinutes;
    }

    public void setPrepMinutes(Integer prepMinutes) {

        this.prepMinutes = prepMinutes;
    }

    public Integer getCookMinutes() {

        return cookMinutes;
    }

    public void setCookMinutes(Integer cookMinutes) {

        this.cookMinutes = cookMinutes;
    }

    public Integer getTotalMinutes() {

        return totalMinutes;
    }

    public void setTotalMinutes(Integer totalMinutes) {

        this.totalMinutes = totalMinutes;
    }

    public Cuisine getCuisine() {

        return cuisine;
    }

    public void setCuisine(Cuisine cuisine) {

        this.cuisine = cuisine;
    }

    public RecipeCategory getCategory() {

        return category;
    }

    public void setCategory(RecipeCategory category) {

        this.category = category;
    }

    public Difficulty getDifficulty() {

        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {

        this.difficulty = difficulty;
    }

    public RecipeSource getSource() {

        return source;
    }

    public void setSource(RecipeSource source) {

        this.source = source;
    }

    public int getCurrentRevisionNo() {

        return currentRevisionNo;
    }

    public RecipeStatus getStatus() {

        return status;
    }

    public Instant getCreatedAt() {

        return createdAt;
    }

    public Instant getUpdatedAt() {

        return updatedAt;
    }

    public List<RecipeIngredient> getIngredients() {

        return ingredients;
    }

    public List<RecipeStep> getSteps() {

        return steps;
    }

    public Set<Tag> getTags() {

        return tags;
    }

    public Set<Diet> getDiets() {

        return diets;
    }
}
