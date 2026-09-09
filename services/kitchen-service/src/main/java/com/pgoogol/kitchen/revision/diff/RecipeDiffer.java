package com.pgoogol.kitchen.revision.diff;

import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Alternative;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Header;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Ingredient;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Step;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.TermRef;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Porównanie dwóch stanów przepisu — serce dziennika zmian.
 *
 * <p>Wiersze kojarzy się WYŁĄCZNIE po identyfikatorze z bazy. Dzięki temu zmiana
 * ilości cebuli jest jedną zmianą pola, a nie skasowaniem jednego wiersza
 * i dodaniem drugiego. Za dopasowanie tego, co przyszło z formularza albo
 * z importu, do istniejących wierszy odpowiada warstwa zapisu — tutaj obie
 * strony mają już nadane identyfikatory.</p>
 *
 * <p>Klasa jest bezstanowa i nie zna Springa: bierze dwa stany, oddaje listę zmian.</p>
 */
public class RecipeDiffer {

    public List<FieldChange> diff(RecipeSnapshot before, RecipeSnapshot after) {

        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        List<FieldChange> changes = new ArrayList<>();
        diffHeader(before.header(), after.header(), changes);
        diffIngredients(before.ingredients(), after.ingredients(), changes);
        diffSteps(before.steps(), after.steps(), changes);
        diffTerms(TargetType.TAG, before.tags(), after.tags(), changes);
        diffTerms(TargetType.DIET, before.diets(), after.diets(), changes);
        return List.copyOf(changes);
    }

    private void diffHeader(Header before, Header after, List<FieldChange> changes) {

        compare(TargetType.RECIPE, null, after.title(), "title", before.title(), after.title(), changes);
        compare(TargetType.RECIPE, null, after.title(), "description",
            before.description(), after.description(), changes);
        compare(TargetType.RECIPE, null, after.title(), "servingsAmount",
            before.servingsAmount(), after.servingsAmount(), changes);
        compare(TargetType.RECIPE, null, after.title(), "servingsUnit",
            before.servingsUnit(), after.servingsUnit(), changes);
        compare(TargetType.RECIPE, null, after.title(), "prepMinutes",
            before.prepMinutes(), after.prepMinutes(), changes);
        compare(TargetType.RECIPE, null, after.title(), "cookMinutes",
            before.cookMinutes(), after.cookMinutes(), changes);
        compare(TargetType.RECIPE, null, after.title(), "totalMinutes",
            before.totalMinutes(), after.totalMinutes(), changes);
        compare(TargetType.RECIPE, null, after.title(), "difficulty",
            before.difficulty(), after.difficulty(), changes);
        compareTerm(after.title(), "cuisine", before.cuisine(), after.cuisine(), changes);
        compareTerm(after.title(), "category", before.category(), after.category(), changes);
    }

    private void diffIngredients(List<Ingredient> before, List<Ingredient> after,
                                 List<FieldChange> changes) {

        Map<Long, Ingredient> byId = index(before, Ingredient::id);
        after.forEach(line -> diffIngredient(byId.remove(line.id()), line, changes));
        byId.values().forEach(line -> removeIngredient(line, changes));
    }

    private void diffIngredient(Ingredient before, Ingredient after, List<FieldChange> changes) {

        if (Objects.isNull(before)) {

            changes.add(FieldChange.added(TargetType.INGREDIENT, after.id(), after.label()));
            after.alternatives().forEach(alternative ->
                changes.add(FieldChange.added(TargetType.ALTERNATIVE, alternative.id(), alternative.label())));
            return;
        }
        TargetType type = TargetType.INGREDIENT;
        Long id = after.id();
        String label = after.label();
        movedIfNeeded(type, id, label, before.position(), after.position(), changes);
        compare(type, id, label, "groupLabel", before.groupLabel(), after.groupLabel(), changes);
        compare(type, id, label, "ingredientId", before.ingredientId(), after.ingredientId(), changes);
        compare(type, id, label, "displayName", before.displayName(), after.displayName(), changes);
        compare(type, id, label, "sourceText", before.sourceText(), after.sourceText(), changes);
        compare(type, id, label, "quantityMin", before.quantityMin(), after.quantityMin(), changes);
        compare(type, id, label, "quantityMax", before.quantityMax(), after.quantityMax(), changes);
        compare(type, id, label, "unitId", before.unitId(), after.unitId(), changes);
        compare(type, id, label, "quantityText", before.quantityText(), after.quantityText(), changes);
        compare(type, id, label, "preparation", before.preparation(), after.preparation(), changes);
        compare(type, id, label, "optional", before.optional(), after.optional(), changes);
        compare(type, id, label, "note", before.note(), after.note(), changes);
        diffAlternatives(before.alternatives(), after.alternatives(), changes);
    }

    private void removeIngredient(Ingredient line, List<FieldChange> changes) {

        line.alternatives().forEach(alternative -> changes.add(
            FieldChange.removed(TargetType.ALTERNATIVE, alternative.id(), alternative.label(), alternative)));
        changes.add(FieldChange.removed(TargetType.INGREDIENT, line.id(), line.label(), line));
    }

    private void diffAlternatives(List<Alternative> before, List<Alternative> after,
                                  List<FieldChange> changes) {

        Map<Long, Alternative> byId = index(before, Alternative::id);
        after.forEach(line -> diffAlternative(byId.remove(line.id()), line, changes));
        byId.values().forEach(line -> changes.add(
            FieldChange.removed(TargetType.ALTERNATIVE, line.id(), line.label(), line)));
    }

    private void diffAlternative(Alternative before, Alternative after, List<FieldChange> changes) {

        if (Objects.isNull(before)) {

            changes.add(FieldChange.added(TargetType.ALTERNATIVE, after.id(), after.label()));
            return;
        }
        TargetType type = TargetType.ALTERNATIVE;
        Long id = after.id();
        String label = after.label();
        movedIfNeeded(type, id, label, before.position(), after.position(), changes);
        compare(type, id, label, "ingredientId", before.ingredientId(), after.ingredientId(), changes);
        compare(type, id, label, "displayName", before.displayName(), after.displayName(), changes);
        compare(type, id, label, "quantityMin", before.quantityMin(), after.quantityMin(), changes);
        compare(type, id, label, "quantityMax", before.quantityMax(), after.quantityMax(), changes);
        compare(type, id, label, "unitId", before.unitId(), after.unitId(), changes);
        compare(type, id, label, "quantityText", before.quantityText(), after.quantityText(), changes);
        compare(type, id, label, "note", before.note(), after.note(), changes);
    }

    private void diffSteps(List<Step> before, List<Step> after, List<FieldChange> changes) {

        Map<Long, Step> byId = index(before, Step::id);
        after.forEach(line -> diffStep(byId.remove(line.id()), line, changes));
        byId.values().forEach(line -> changes.add(
            FieldChange.removed(TargetType.STEP, line.id(), line.label(), line)));
    }

    private void diffStep(Step before, Step after, List<FieldChange> changes) {

        if (Objects.isNull(before)) {

            changes.add(FieldChange.added(TargetType.STEP, after.id(), after.label()));
            return;
        }
        TargetType type = TargetType.STEP;
        Long id = after.id();
        String label = after.label();
        movedIfNeeded(type, id, label, before.position(), after.position(), changes);
        compare(type, id, label, "groupLabel", before.groupLabel(), after.groupLabel(), changes);
        compare(type, id, label, "text", before.text(), after.text(), changes);
        compare(type, id, label, "sourceText", before.sourceText(), after.sourceText(), changes);
        compare(type, id, label, "durationMinutes",
            before.durationMinutes(), after.durationMinutes(), changes);
        compare(type, id, label, "temperatureC", before.temperatureC(), after.temperatureC(), changes);
        compare(type, id, label, "temperatureNote",
            before.temperatureNote(), after.temperatureNote(), changes);
        diffStepIngredients(id, label, before.ingredientIds(), after.ingredientIds(), changes);
        diffStepEquipment(id, before.equipment(), after.equipment(), changes);
    }

    private void diffStepIngredients(Long stepId, String label, Set<Long> before, Set<Long> after,
                                     List<FieldChange> changes) {

        after.stream().filter(id -> !before.contains(id)).forEach(id -> changes.add(
            new FieldChange(TargetType.STEP_INGREDIENT, id, label,
                ChangeOperation.ADD, null, null, String.valueOf(stepId), null)));
        before.stream().filter(id -> !after.contains(id)).forEach(id -> changes.add(
            new FieldChange(TargetType.STEP_INGREDIENT, id, label,
                ChangeOperation.REMOVE, null, String.valueOf(stepId), null, null)));
    }

    private void diffStepEquipment(Long stepId, Set<TermRef> before, Set<TermRef> after,
                                   List<FieldChange> changes) {

        Set<Long> beforeIds = ids(before);
        Set<Long> afterIds = ids(after);
        after.stream().filter(term -> !beforeIds.contains(term.id())).forEach(term -> changes.add(
            new FieldChange(TargetType.STEP_EQUIPMENT, term.id(), term.name(),
                ChangeOperation.ADD, null, null, String.valueOf(stepId), null)));
        before.stream().filter(term -> !afterIds.contains(term.id())).forEach(term -> changes.add(
            new FieldChange(TargetType.STEP_EQUIPMENT, term.id(), term.name(),
                ChangeOperation.REMOVE, null, String.valueOf(stepId), null, null)));
    }

    private void diffTerms(TargetType type, Set<TermRef> before, Set<TermRef> after,
                           List<FieldChange> changes) {

        Set<Long> beforeIds = ids(before);
        Set<Long> afterIds = ids(after);
        after.stream().filter(term -> !beforeIds.contains(term.id())).forEach(term ->
            changes.add(FieldChange.added(type, term.id(), term.name())));
        before.stream().filter(term -> !afterIds.contains(term.id())).forEach(term ->
            changes.add(new FieldChange(type, term.id(), term.name(),
                ChangeOperation.REMOVE, null, null, null, null)));
    }

    private void compareTerm(String label, String field, TermRef before, TermRef after,
                             List<FieldChange> changes) {

        Long beforeId = idOf(before);
        Long afterId = idOf(after);
        if (Objects.equals(beforeId, afterId)) {

            return;
        }
        changes.add(FieldChange.updated(TargetType.RECIPE, null, label, field,
            nameOf(before), nameOf(after)));
    }

    private void compare(TargetType type, Long id, String label, String field,
                         Object before, Object after, List<FieldChange> changes) {

        if (sameValue(before, after)) {

            return;
        }
        changes.add(FieldChange.updated(type, id, label, field, text(before), text(after)));
    }

    private void movedIfNeeded(TargetType type, Long id, String label, int before, int after,
                               List<FieldChange> changes) {

        if (before == after) {

            return;
        }
        changes.add(FieldChange.moved(type, id, label, before, after));
    }

    /**
     * Liczby porównujemy wartością, nie reprezentacją: {@code 1.0} i {@code 1.000}
     * to ta sama ilość, a baza potrafi oddać ją z inną liczbą miejsc po przecinku
     * niż ta, którą zapisano.
     */
    private boolean sameValue(Object before, Object after) {

        if (before instanceof BigDecimal first && after instanceof BigDecimal second) {

            return first.compareTo(second) == 0;
        }
        return Objects.equals(before, after);
    }

    private <T> Map<Long, T> index(List<T> lines, Function<T, Long> id) {

        Map<Long, T> byId = new LinkedHashMap<>();
        lines.forEach(line -> byId.put(id.apply(line), line));
        return byId;
    }

    private Set<Long> ids(Set<TermRef> terms) {

        return terms.stream().map(TermRef::id).collect(Collectors.toUnmodifiableSet());
    }

    private Long idOf(TermRef term) {

        if (Objects.isNull(term)) {

            return null;
        }
        return term.id();
    }

    private String nameOf(TermRef term) {

        if (Objects.isNull(term)) {

            return null;
        }
        return term.name();
    }

    private String text(Object value) {

        if (Objects.isNull(value)) {

            return null;
        }
        if (value instanceof BigDecimal number) {

            return number.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
