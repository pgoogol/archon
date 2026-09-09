package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.api.RecipeResponses.AlternativeResponse;
import com.pgoogol.kitchen.api.RecipeResponses.IngredientLineResponse;
import com.pgoogol.kitchen.api.RecipeResponses.NoteResponse;
import com.pgoogol.kitchen.api.RecipeResponses.RecipePageResponse;
import com.pgoogol.kitchen.api.RecipeResponses.RecipeResponse;
import com.pgoogol.kitchen.api.RecipeResponses.RecipeSummaryResponse;
import com.pgoogol.kitchen.api.RecipeResponses.SourceResponse;
import com.pgoogol.kitchen.api.RecipeResponses.StepResponse;
import com.pgoogol.kitchen.dictionary.domain.DictionaryTerm;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeIngredient;
import com.pgoogol.kitchen.recipe.domain.RecipeIngredientAlternative;
import com.pgoogol.kitchen.recipe.domain.RecipeNote;
import com.pgoogol.kitchen.recipe.domain.RecipeSource;
import com.pgoogol.kitchen.recipe.domain.RecipeStep;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Encje na odpowiedzi API. Nic tu nie decyduje — same przepisania. */
@Component
public class RecipeApiMapper {

    public RecipeResponse toResponse(Recipe recipe) {

        return new RecipeResponse(
            recipe.getId(),
            recipe.getTitle(),
            recipe.getDescription(),
            recipe.getServingsAmount(),
            recipe.getServingsUnit(),
            recipe.getPrepMinutes(),
            recipe.getCookMinutes(),
            recipe.getTotalMinutes(),
            nameOf(recipe.getCuisine()),
            nameOf(recipe.getCategory()),
            recipe.getDifficulty(),
            recipe.getStatus(),
            recipe.getCurrentRevisionNo(),
            recipe.getCreatedAt(),
            recipe.getUpdatedAt(),
            source(recipe.getSource()),
            recipe.getIngredients().stream().map(this::ingredient).toList(),
            recipe.getSteps().stream().map(this::step).toList(),
            names(recipe.getTags()),
            names(recipe.getDiets()));
    }

    public RecipePageResponse toPage(Page<Recipe> page) {

        List<RecipeSummaryResponse> items = page.getContent().stream().map(this::summary).toList();
        return new RecipePageResponse(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }

    public NoteResponse toResponse(RecipeNote note) {

        return new NoteResponse(note.getId(), note.getBody(), note.getCreatedAt(), note.getUpdatedAt());
    }

    private RecipeSummaryResponse summary(Recipe recipe) {

        return new RecipeSummaryResponse(
            recipe.getId(),
            recipe.getTitle(),
            recipe.getDescription(),
            recipe.getTotalMinutes(),
            nameOf(recipe.getCuisine()),
            nameOf(recipe.getCategory()),
            recipe.getDifficulty(),
            recipe.getIngredients().size(),
            recipe.getSteps().size(),
            recipe.getCreatedAt());
    }

    private IngredientLineResponse ingredient(RecipeIngredient line) {

        Unit unit = line.getUnit();
        return new IngredientLineResponse(
            line.getId(),
            line.getPosition(),
            line.getGroupLabel(),
            idOf(line.getIngredient()),
            line.getDisplayName(),
            line.getSourceText(),
            line.getQuantityMin(),
            line.getQuantityMax(),
            codeOf(unit),
            unitName(unit),
            line.getQuantityText(),
            line.getPreparation(),
            line.isOptional(),
            line.getNote(),
            line.getAlternatives().stream().map(this::alternative).toList());
    }

    private AlternativeResponse alternative(RecipeIngredientAlternative line) {

        Unit unit = line.getUnit();
        return new AlternativeResponse(
            line.getId(),
            line.getPosition(),
            idOf(line.getIngredient()),
            line.getDisplayName(),
            line.getQuantityMin(),
            line.getQuantityMax(),
            codeOf(unit),
            unitName(unit),
            line.getQuantityText(),
            line.getNote());
    }

    private StepResponse step(RecipeStep step) {

        List<Long> ingredientIds = step.getIngredients().stream()
            .map(RecipeIngredient::getId)
            .filter(Objects::nonNull)
            .toList();
        return new StepResponse(
            step.getId(),
            step.getPosition(),
            step.getGroupLabel(),
            step.getText(),
            step.getSourceText(),
            step.getDurationMinutes(),
            step.getTemperatureC(),
            step.getTemperatureNote(),
            ingredientIds,
            names(step.getEquipment()));
    }

    private SourceResponse source(RecipeSource source) {

        if (Objects.isNull(source)) {

            return null;
        }
        return new SourceResponse(
            source.getKind(), source.getUrl(), source.getSiteName(), source.getAuthor());
    }

    private List<String> names(Set<? extends DictionaryTerm> terms) {

        return terms.stream().map(DictionaryTerm::getName).sorted().toList();
    }

    private String nameOf(DictionaryTerm term) {

        if (Objects.isNull(term)) {

            return null;
        }
        return term.getName();
    }

    private Long idOf(Ingredient ingredient) {

        if (Objects.isNull(ingredient)) {

            return null;
        }
        return ingredient.getId();
    }

    private String codeOf(Unit unit) {

        if (Objects.isNull(unit)) {

            return null;
        }
        return unit.getCode();
    }

    private String unitName(Unit unit) {

        if (Objects.isNull(unit)) {

            return null;
        }
        return unit.getName();
    }
}
