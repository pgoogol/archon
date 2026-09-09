package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.dictionary.domain.DictionaryTerm;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.recipe.domain.Difficulty;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeIngredient;
import com.pgoogol.kitchen.recipe.domain.RecipeIngredientAlternative;
import com.pgoogol.kitchen.recipe.domain.RecipeStep;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Alternative;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Header;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Step;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.TermRef;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Zdjęcie stanu przepisu do porównania. Powstaje z encji przed zmianą i po
 * zmianie — różnica tych dwóch obrazów jest dziennikiem.
 *
 * <p>Wołać wyłącznie w transakcji z załadowaną zawartością przepisu.</p>
 */
@Component
public class RecipeSnapshots {

    public RecipeSnapshot of(Recipe recipe) {

        return new RecipeSnapshot(
            header(recipe),
            recipe.getIngredients().stream().map(this::ingredient).toList(),
            recipe.getSteps().stream().map(this::step).toList(),
            terms(recipe.getTags()),
            terms(recipe.getDiets()));
    }

    /** Stan „przed" dla przepisu, którego jeszcze nie było. */
    public RecipeSnapshot empty() {

        Header header = new Header(null, null, null, null, null, null, null, null, null, null);
        return new RecipeSnapshot(header, List.of(), List.of(), Set.of(), Set.of());
    }

    private Header header(Recipe recipe) {

        return new Header(
            recipe.getTitle(),
            recipe.getDescription(),
            recipe.getServingsAmount(),
            recipe.getServingsUnit(),
            recipe.getPrepMinutes(),
            recipe.getCookMinutes(),
            recipe.getTotalMinutes(),
            term(recipe.getCuisine()),
            term(recipe.getCategory()),
            difficulty(recipe.getDifficulty()));
    }

    private RecipeSnapshot.Ingredient ingredient(RecipeIngredient line) {

        List<Alternative> alternatives = line.getAlternatives().stream().map(this::alternative).toList();
        return new RecipeSnapshot.Ingredient(
            line.getId(),
            line.getPosition(),
            line.getGroupLabel(),
            idOf(line.getIngredient()),
            line.getDisplayName(),
            line.getSourceText(),
            line.getQuantityMin(),
            line.getQuantityMax(),
            idOf(line.getUnit()),
            line.getQuantityText(),
            line.getPreparation(),
            line.isOptional(),
            line.getNote(),
            alternatives);
    }

    private Alternative alternative(RecipeIngredientAlternative line) {

        return new Alternative(
            line.getId(),
            line.getPosition(),
            idOf(line.getIngredient()),
            line.getDisplayName(),
            line.getQuantityMin(),
            line.getQuantityMax(),
            idOf(line.getUnit()),
            line.getQuantityText(),
            line.getNote());
    }

    private Step step(RecipeStep step) {

        Set<Long> ingredientIds = step.getIngredients().stream()
            .map(RecipeIngredient::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Step(
            step.getId(),
            step.getPosition(),
            step.getGroupLabel(),
            step.getText(),
            step.getSourceText(),
            step.getDurationMinutes(),
            step.getTemperatureC(),
            step.getTemperatureNote(),
            ingredientIds,
            terms(step.getEquipment()));
    }

    private Set<TermRef> terms(Set<? extends DictionaryTerm> source) {

        return source.stream()
            .map(term -> new TermRef(term.getId(), term.getName()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private TermRef term(DictionaryTerm value) {

        if (Objects.isNull(value)) {

            return null;
        }
        return new TermRef(value.getId(), value.getName());
    }

    private Long idOf(Unit unit) {

        if (Objects.isNull(unit)) {

            return null;
        }
        return unit.getId();
    }

    private Long idOf(Ingredient ingredient) {

        if (Objects.isNull(ingredient)) {

            return null;
        }
        return ingredient.getId();
    }

    private String difficulty(Difficulty difficulty) {

        if (Objects.isNull(difficulty)) {

            return null;
        }
        return difficulty.name();
    }
}
