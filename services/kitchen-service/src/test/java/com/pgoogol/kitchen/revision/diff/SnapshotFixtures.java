package com.pgoogol.kitchen.revision.diff;

import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Header;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Ingredient;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Step;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.TermRef;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Stany przepisu do testów dziennika. Pisanie ich w miejscu użycia zajmowałoby
 * pół ekranu na przypadek i zasłaniało to, co w teście naprawdę się zmienia.
 */
final class SnapshotFixtures {

    private SnapshotFixtures() {

    }

    static Header header(String title) {

        return new Header(title, "opis", new BigDecimal("4"), "porcje", 15, 30, 45,
            new TermRef(1L, "polska"), new TermRef(2L, "danie główne"), "EASY");
    }

    static Ingredient ingredient(Long id, int position, String name, String quantity) {

        BigDecimal amount = new BigDecimal(quantity);
        return new Ingredient(id, position, null, 100L, name, null, amount, amount, 5L,
            null, null, false, null, List.of());
    }

    static Step step(Long id, int position, String text) {

        return new Step(id, position, null, text, null, null, null, null, Set.of(), Set.of());
    }

    static RecipeSnapshot snapshot(Header header, List<Ingredient> ingredients, List<Step> steps) {

        return new RecipeSnapshot(header, ingredients, steps, Set.of(), Set.of());
    }

    static RecipeSnapshot snapshot(List<Ingredient> ingredients, List<Step> steps) {

        return snapshot(header("Sernik"), ingredients, steps);
    }
}
