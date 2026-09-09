package com.pgoogol.kitchen.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Uwaga do przepisu: „za słone", „następnym razem połowa cukru". Dotyczy całego
 * przepisu, nie konkretnej wersji — notatka z gotowania przeżywa każdą edycję.
 */
@Entity
@Table(name = "recipe_note")
public class RecipeNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecipeNote() {

    }

    public RecipeNote(Recipe recipe, String body) {

        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.body = Objects.requireNonNull(body, "body");
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void edit(String body) {

        this.body = Objects.requireNonNull(body, "body");
        this.updatedAt = Instant.now();
    }

    public Long getId() {

        return id;
    }

    public Recipe getRecipe() {

        return recipe;
    }

    public String getBody() {

        return body;
    }

    public Instant getCreatedAt() {

        return createdAt;
    }

    public Instant getUpdatedAt() {

        return updatedAt;
    }
}
