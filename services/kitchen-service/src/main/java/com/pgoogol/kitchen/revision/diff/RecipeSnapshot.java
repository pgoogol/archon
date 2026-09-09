package com.pgoogol.kitchen.revision.diff;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Odczepiony stan przepisu — wejście i wyjście rachunku na dzienniku. Nigdy nie
 * jest encją: powstaje z encji przed zmianą i po zmianie, a porównanie obu
 * wersji daje listę zmienionych pól.
 *
 * <p>Wiersze niosą identyfikatory z bazy, bo to po nich rozpoznaje się, że to
 * ten sam składnik z inną ilością, a nie inny składnik.</p>
 */
public record RecipeSnapshot(
    Header header,
    List<Ingredient> ingredients,
    List<Step> steps,
    Set<TermRef> tags,
    Set<TermRef> diets) {

    public RecipeSnapshot {

        Objects.requireNonNull(header, "header");
        ingredients = List.copyOf(ingredients);
        steps = List.copyOf(steps);
        tags = Set.copyOf(tags);
        diets = Set.copyOf(diets);
    }

    /** Nagłówek przepisu — pola, które nie są listą. */
    public record Header(
        String title,
        String description,
        BigDecimal servingsAmount,
        String servingsUnit,
        Integer prepMinutes,
        Integer cookMinutes,
        Integer totalMinutes,
        TermRef cuisine,
        TermRef category,
        String difficulty) {

    }

    /** Odwołanie do pozycji słownika: identyfikator do porównania, nazwa do historii. */
    public record TermRef(Long id, String name) {

    }

    /** Wiersz składnika razem z zamiennikami. */
    public record Ingredient(
        Long id,
        int position,
        String groupLabel,
        Long ingredientId,
        String displayName,
        String sourceText,
        BigDecimal quantityMin,
        BigDecimal quantityMax,
        Long unitId,
        String quantityText,
        String preparation,
        boolean optional,
        String note,
        List<Alternative> alternatives) implements Line {

        public Ingredient {

            alternatives = List.copyOf(alternatives);
        }

        @Override
        public String label() {

            return displayName;
        }
    }

    /** Zamiennik składnika: „albo masło, albo margaryna". */
    public record Alternative(
        Long id,
        int position,
        Long ingredientId,
        String displayName,
        BigDecimal quantityMin,
        BigDecimal quantityMax,
        Long unitId,
        String quantityText,
        String note) implements Line {

        @Override
        public String label() {

            return displayName;
        }
    }

    /** Krok przygotowania razem z powiązaniami do składników i sprzętu. */
    public record Step(
        Long id,
        int position,
        String groupLabel,
        String text,
        String sourceText,
        Integer durationMinutes,
        Integer temperatureC,
        String temperatureNote,
        Set<Long> ingredientIds,
        Set<TermRef> equipment) implements Line {

        public Step {

            ingredientIds = Set.copyOf(ingredientIds);
            equipment = Set.copyOf(equipment);
        }

        @Override
        public String label() {

            return "krok " + position;
        }
    }

    /**
     * Wspólny mianownik wierszy, które da się dodać i usunąć. Usunięty wiersz
     * wędruje w całości do dziennika — bez tego nie dałoby się go odtworzyć.
     */
    public sealed interface Line permits Ingredient, Alternative, Step {

        Long id();

        String label();
    }
}
