package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.dictionary.application.TermService;
import com.pgoogol.kitchen.dictionary.domain.Diet;
import com.pgoogol.kitchen.dictionary.domain.Equipment;
import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;
import com.pgoogol.kitchen.dictionary.domain.Tag;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.dictionary.units.UnitConversion;
import com.pgoogol.kitchen.dictionary.units.UnitConverter;
import com.pgoogol.kitchen.ingredient.application.IngredientResolver;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft.DraftAlternative;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft.DraftIngredient;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft.DraftStep;
import com.pgoogol.kitchen.recipe.domain.RecipeIngredient;
import com.pgoogol.kitchen.recipe.domain.RecipeIngredientAlternative;
import com.pgoogol.kitchen.recipe.domain.RecipeStep;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Nanoszenie treści z {@link RecipeDraft} na encje przepisu.
 *
 * <p>Sedno jest w dopasowaniu wierszy: linia ze szkicu trafia najpierw do wiersza
 * o tym samym identyfikatorze (tak robi formularz), potem do wiersza o tej samej
 * nazwie (tak wchodzi ponowny import), a dopiero na końcu powstaje nowy wiersz.
 * Bez tego każda edycja czytałaby się w historii jako „usunięto osiem składników,
 * dodano osiem".</p>
 *
 * <p>Jednostki przechodzą przez {@link UnitConverter}: formularz podaje kod ze
 * słownika i konwersja jest wtedy niczym, ale przepis z czatu potrafi przynieść
 * „2 cups", a to ma wylądować w szklankach. Jednostki nierozpoznanej nie
 * zgadujemy — jej zapis zostaje w tekstowym polu ilości.</p>
 */
@Component
@RequiredArgsConstructor
public class RecipeMutator {

    private final TermService terms;
    private final IngredientResolver ingredientResolver;

    public void apply(Recipe recipe, RecipeDraft draft) {

        applyHeader(recipe, draft);
        List<RecipeIngredient> ingredients = applyIngredients(recipe, draft);
        applySteps(recipe, draft, ingredients);
        applyDictionaries(recipe, draft);
    }

    private void applyHeader(Recipe recipe, RecipeDraft draft) {

        recipe.setTitle(draft.title().trim());
        recipe.setDescription(draft.description());
        recipe.setServingsAmount(draft.servingsAmount());
        recipe.setServingsUnit(draft.servingsUnit());
        recipe.setPrepMinutes(draft.prepMinutes());
        recipe.setCookMinutes(draft.cookMinutes());
        recipe.setTotalMinutes(draft.totalMinutes());
        recipe.setDifficulty(draft.difficulty());
    }

    private List<RecipeIngredient> applyIngredients(Recipe recipe, RecipeDraft draft) {

        List<DraftIngredient> lines = CollectionUtils.emptyIfNull(draft.ingredients()).stream().toList();
        List<RecipeIngredient> pool = new ArrayList<>(recipe.getIngredients());
        List<RecipeIngredient> result = new ArrayList<>();
        IntStream.range(0, lines.size())
            .forEach(index -> result.add(applyIngredient(recipe, pool, index, lines.get(index))));
        recipe.getIngredients().removeAll(pool);
        return result;
    }

    private RecipeIngredient applyIngredient(Recipe recipe, List<RecipeIngredient> pool,
                                             int index, DraftIngredient line) {

        RecipeIngredient entity = matchIngredient(pool, line);
        if (Objects.isNull(entity)) {

            entity = recipe.addIngredient(index, line.displayName().trim());
        }
        entity.setPosition(index);
        entity.setGroupLabel(blankToNull(line.group()));
        entity.setDisplayName(line.displayName().trim());
        entity.setSourceText(line.sourceText());
        entity.setPreparation(blankToNull(line.preparation()));
        entity.setOptional(Boolean.TRUE.equals(line.optional()));
        entity.setNote(blankToNull(line.note()));
        entity.setIngredient(catalogEntry(line.canonicalName(), line.displayName()));
        applyQuantity(entity, line);
        applyAlternatives(entity, line);
        return entity;
    }

    private void applyQuantity(RecipeIngredient entity, DraftIngredient line) {

        UnitConversion conversion =
            UnitConverter.convert(line.quantityMin(), line.quantityMax(), line.unit());
        entity.setQuantityMin(conversion.min());
        entity.setQuantityMax(conversion.max());
        entity.setUnit(unit(conversion.unitCode()));
        entity.setQuantityText(quantityText(line.quantityText(), conversion, line.unit()));
    }

    private void applyAlternatives(RecipeIngredient entity, DraftIngredient line) {

        List<DraftAlternative> lines = CollectionUtils.emptyIfNull(line.alternatives()).stream().toList();
        List<RecipeIngredientAlternative> pool = new ArrayList<>(entity.getAlternatives());
        IntStream.range(0, lines.size())
            .forEach(index -> applyAlternative(entity, pool, index, lines.get(index)));
        entity.getAlternatives().removeAll(pool);
    }

    private void applyAlternative(RecipeIngredient parent, List<RecipeIngredientAlternative> pool,
                                  int index, DraftAlternative line) {

        RecipeIngredientAlternative entity = matchAlternative(pool, line);
        if (Objects.isNull(entity)) {

            entity = parent.addAlternative(index, line.displayName().trim());
        }
        UnitConversion conversion =
            UnitConverter.convert(line.quantityMin(), line.quantityMax(), line.unit());
        entity.setPosition(index);
        entity.setDisplayName(line.displayName().trim());
        entity.setNote(blankToNull(line.note()));
        entity.setIngredient(catalogEntry(line.canonicalName(), line.displayName()));
        entity.setQuantityMin(conversion.min());
        entity.setQuantityMax(conversion.max());
        entity.setUnit(unit(conversion.unitCode()));
        entity.setQuantityText(quantityText(line.quantityText(), conversion, line.unit()));
    }

    private void applySteps(Recipe recipe, RecipeDraft draft, List<RecipeIngredient> ingredients) {

        List<DraftStep> lines = CollectionUtils.emptyIfNull(draft.steps()).stream().toList();
        List<RecipeStep> pool = new ArrayList<>(recipe.getSteps());
        IntStream.range(0, lines.size())
            .forEach(index -> applyStep(recipe, pool, index, lines.get(index), ingredients));
        recipe.getSteps().removeAll(pool);
    }

    private void applyStep(Recipe recipe, List<RecipeStep> pool, int index, DraftStep line,
                           List<RecipeIngredient> ingredients) {

        RecipeStep entity = matchStep(pool, line, index);
        if (Objects.isNull(entity)) {

            entity = recipe.addStep(index, line.text().trim());
        }
        entity.setPosition(index);
        entity.setGroupLabel(blankToNull(line.group()));
        entity.setText(line.text().trim());
        entity.setSourceText(line.sourceText());
        entity.setDurationMinutes(line.durationMinutes());
        entity.setTemperatureC(line.temperatureC());
        entity.setTemperatureNote(blankToNull(line.temperatureNote()));
        applyStepIngredients(entity, line, ingredients);
        Set<Equipment> resolved = terms.resolveEquipment(line.equipment());
        entity.getEquipment().clear();
        entity.getEquipment().addAll(resolved);
    }

    /**
     * Powiązania budujemy od zera z pozycji na liście składników — szkic mówi
     * „krok drugi używa składników 0 i 3", a nie zna identyfikatorów wierszy.
     */
    private void applyStepIngredients(RecipeStep step, DraftStep line,
                                      List<RecipeIngredient> ingredients) {

        Set<RecipeIngredient> linked = new LinkedHashSet<>();
        CollectionUtils.emptyIfNull(line.ingredientIndexes()).stream()
            .filter(Objects::nonNull)
            .filter(index -> index >= 0 && index < ingredients.size())
            .forEach(index -> linked.add(ingredients.get(index)));
        step.getIngredients().clear();
        step.getIngredients().addAll(linked);
    }

    private void applyDictionaries(Recipe recipe, RecipeDraft draft) {

        recipe.setCuisine(terms.findCuisine(draft.cuisine()).orElse(null));
        recipe.setCategory(terms.findCategory(draft.category()).orElse(null));
        Set<Tag> tags = terms.resolveTags(draft.tags());
        recipe.getTags().clear();
        recipe.getTags().addAll(tags);
        Set<Diet> diets = terms.findDiets(draft.diets());
        recipe.getDiets().clear();
        recipe.getDiets().addAll(diets);
    }

    private RecipeIngredient matchIngredient(List<RecipeIngredient> pool, DraftIngredient line) {

        RecipeIngredient byId = takeById(pool, line.id(), RecipeIngredient::getId);
        if (Objects.nonNull(byId)) {

            return byId;
        }
        return takeByName(pool, line.displayName(), RecipeIngredient::getDisplayName);
    }

    private RecipeIngredientAlternative matchAlternative(List<RecipeIngredientAlternative> pool,
                                                         DraftAlternative line) {

        RecipeIngredientAlternative byId =
            takeById(pool, line.id(), RecipeIngredientAlternative::getId);
        if (Objects.nonNull(byId)) {

            return byId;
        }
        return takeByName(pool, line.displayName(), RecipeIngredientAlternative::getDisplayName);
    }

    /**
     * Krok bez identyfikatora dopasowujemy po pozycji, nie po treści: przy
     * ponownym imporcie tekst kroku bywa przeredagowany, a kolejność zostaje.
     */
    private RecipeStep matchStep(List<RecipeStep> pool, DraftStep line, int index) {

        RecipeStep byId = takeById(pool, line.id(), RecipeStep::getId);
        if (Objects.nonNull(byId)) {

            return byId;
        }
        Optional<RecipeStep> byPosition = pool.stream()
            .filter(step -> step.getPosition() == index)
            .findFirst();
        byPosition.ifPresent(pool::remove);
        return byPosition.orElse(null);
    }

    private <T> T takeById(List<T> pool, Long id, Function<T, Long> idOf) {

        if (Objects.isNull(id)) {

            return null;
        }
        Optional<T> found = pool.stream().filter(item -> Objects.equals(idOf.apply(item), id)).findFirst();
        found.ifPresent(pool::remove);
        return found.orElse(null);
    }

    private <T> T takeByName(List<T> pool, String name, Function<T, String> nameOf) {

        String normalized = NameNormalizer.normalize(name);
        if (Objects.isNull(normalized)) {

            return null;
        }
        Optional<T> found = pool.stream()
            .filter(item -> Objects.equals(NameNormalizer.normalize(nameOf.apply(item)), normalized))
            .findFirst();
        found.ifPresent(pool::remove);
        return found.orElse(null);
    }

    private Ingredient catalogEntry(String canonicalName, String displayName) {

        String name = Objects.requireNonNullElse(blankToNull(canonicalName), displayName);
        Optional<Ingredient> resolved = ingredientResolver.resolveOrCreate(name);
        return resolved.orElse(null);
    }

    private Unit unit(String code) {

        Optional<Unit> resolved = terms.findUnit(code);
        return resolved.orElse(null);
    }

    /**
     * Nierozpoznana jednostka nie znika: jej zapis dopisuje się do tekstowej
     * ilości, żeby „1 pinch of salt" nie zamieniło się w gołą jedynkę.
     */
    private String quantityText(String given, UnitConversion conversion, String rawUnit) {

        String text = blankToNull(given);
        if (Objects.nonNull(conversion.unitCode()) || Objects.isNull(blankToNull(rawUnit))) {

            return text;
        }
        if (Objects.isNull(text)) {

            return rawUnit.trim();
        }
        return text;
    }

    private String blankToNull(String value) {

        if (Objects.isNull(value) || value.isBlank()) {

            return null;
        }
        return value.trim();
    }
}
