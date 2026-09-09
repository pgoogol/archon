package com.pgoogol.kitchen.recipe.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Przepis w postaci do zapisania — jedyne wejście warstwy zapisu.
 *
 * <p>Tym samym kształtem posługują się: formularz (nowy przepis i edycja),
 * akceptacja szkicu z importu, przepis przysłany przez czat i przywrócenie
 * starszej wersji. Drugi identyczny typ „tylko dla API" byłby drugą kopią do
 * utrzymania i drugim miejscem, w którym można zapomnieć o walidacji.</p>
 *
 * <p>Słowniki podaje się nazwami, nie identyfikatorami: import dostaje od modelu
 * „kuchnia włoska", a nie liczbę, a formularz i tak wybiera z listy nazw.</p>
 */
@Schema(description = "Przepis w postaci do zapisania — wspólny kształt dla formularza, importu i przywracania wersji")
public record RecipeDraft(

    @NotBlank
    @Size(max = 300)
    String title,

    String description,

    @Positive
    BigDecimal servingsAmount,

    @Size(max = 50)
    @Schema(description = "Czego dotyczą porcje: porcje, sztuki, blacha", example = "porcje")
    String servingsUnit,

    @PositiveOrZero
    @Max(MAX_MINUTES)
    Integer prepMinutes,

    @PositiveOrZero
    @Max(MAX_MINUTES)
    Integer cookMinutes,

    @PositiveOrZero
    @Max(MAX_MINUTES)
    Integer totalMinutes,

    @Schema(description = "Nazwa kuchni ze słownika; nieznana zostaje pominięta", example = "polska")
    String cuisine,

    @Schema(description = "Nazwa kategorii ze słownika", example = "danie główne")
    String category,

    Difficulty difficulty,

    @Valid
    @Size(max = 200)
    List<DraftIngredient> ingredients,

    @Valid
    @Size(max = 200)
    List<DraftStep> steps,

    @Schema(description = "Etykiety własne — nieznane powstają przy zapisie")
    @Size(max = 50)
    List<String> tags,

    @Schema(description = "Nazwy diet ze słownika; nieznane zostają pominięte")
    @Size(max = 20)
    List<String> diets) {

    /** Siedem dni — dłuższy czas w przepisie to pomyłka, nie zakwas. */
    static final int MAX_MINUTES = 10_080;

    @Schema(description = "Składnik przepisu")
    public record DraftIngredient(

        @Schema(description = "Identyfikator istniejącego wiersza; pusty przy nowym. To po nim historia rozpoznaje, że to ten sam składnik z inną ilością")
        Long id,

        @Size(max = 100)
        @Schema(description = "Grupa, do której należy wiersz: na spód, na masę", example = "na spód")
        String group,

        @NotBlank
        @Size(max = 200)
        @Schema(description = "Nazwa jak w przepisie", example = "czerwona cebula")
        String displayName,

        @Size(max = 200)
        @Schema(description = "Nazwa kanoniczna do dopasowania w katalogu; pusta = użyj widocznej", example = "cebula czerwona")
        String canonicalName,

        @Schema(description = "Oryginalna linia ze źródła — zostaje przy tłumaczeniu")
        String sourceText,

        @PositiveOrZero
        BigDecimal quantityMin,

        @PositiveOrZero
        BigDecimal quantityMax,

        @Size(max = 30)
        @Schema(description = "Kod jednostki ze słownika", example = "g")
        String unit,

        @Size(max = 100)
        @Schema(description = "Ilość bez liczby: szczypta, do smaku")
        String quantityText,

        @Size(max = 200)
        @Schema(description = "Sposób przygotowania", example = "posiekana")
        String preparation,

        Boolean optional,

        String note,

        @Valid
        @Size(max = 10)
        List<DraftAlternative> alternatives) {

    }

    @Schema(description = "Zamiennik składnika")
    public record DraftAlternative(

        Long id,

        @NotBlank
        @Size(max = 200)
        String displayName,

        @Size(max = 200)
        String canonicalName,

        @PositiveOrZero
        BigDecimal quantityMin,

        @PositiveOrZero
        BigDecimal quantityMax,

        @Size(max = 30)
        String unit,

        @Size(max = 100)
        String quantityText,

        String note) {

    }

    @Schema(description = "Krok przygotowania")
    public record DraftStep(

        Long id,

        @Size(max = 100)
        String group,

        @NotBlank
        String text,

        String sourceText,

        @PositiveOrZero
        @Max(MAX_MINUTES)
        Integer durationMinutes,

        @Min(0)
        @Max(400)
        Integer temperatureC,

        @Size(max = 100)
        @Schema(example = "termoobieg")
        String temperatureNote,

        @Schema(description = "Pozycje z listy składników użyte w tym kroku, liczone od zera")
        List<Integer> ingredientIndexes,

        @Schema(description = "Sprzęt użyty w kroku — nieznany powstaje przy zapisie")
        @Size(max = 20)
        List<String> equipment) {

    }
}
