package com.pgoogol.kitchen.recipe;

import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft.DraftIngredient;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft.DraftStep;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * Przepisy do testów. Identyfikator nadaje się tu przez refleksję, bo w bazie
 * nadaje go sekwencja, a encja świadomie nie ma na niego settera — test nie ma
 * prawa zmieniać tożsamości wiersza inaczej niż baza.
 */
public final class RecipeFixtures {

    private RecipeFixtures() {

    }

    public static Recipe recipe(Long id, String title) {

        Recipe recipe = new Recipe(title);
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    public static <T> T withId(T entity, Long id) {

        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** Najprostszy poprawny szkic: jeden składnik i jeden krok. */
    public static RecipeDraft draft(String title) {

        return new RecipeDraft(title, null, new BigDecimal("4"), "porcje", 10, 20, 30,
            "polska", "danie główne", null,
            List.of(ingredient("cebula", "1")),
            List.of(step("Posiekaj cebulę")),
            List.of(), List.of());
    }

    public static DraftIngredient ingredient(String name, String quantity) {

        BigDecimal amount = new BigDecimal(quantity);
        return new DraftIngredient(null, null, name, null, null, amount, amount, "szt",
            null, null, false, null, List.of());
    }

    public static DraftStep step(String text) {

        return new DraftStep(null, null, text, null, null, null, null, List.of(), List.of());
    }

    public static RecipeDraft emptyDraft(String title) {

        return new RecipeDraft(title, null, null, null, null, null, null, null, null, null,
            List.of(), List.of(), List.of(), List.of());
    }
}
