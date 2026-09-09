package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.recipe.domain.Difficulty;
import com.pgoogol.kitchen.recipe.domain.RecipeStatus;
import com.pgoogol.kitchen.recipe.domain.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Odpowiedzi przepisu w jednym pliku — komplet kształtów, które opisują jeden
 * zasób, czyta się lepiej razem niż rozsypany po ośmiu plikach po dwa pola.
 */
public final class RecipeResponses {

    private RecipeResponses() {

    }

    @Schema(description = "Przepis z całą zawartością")
    public record RecipeResponse(
        Long id,
        String title,
        String description,
        BigDecimal servingsAmount,
        String servingsUnit,
        Integer prepMinutes,
        Integer cookMinutes,
        Integer totalMinutes,
        String cuisine,
        String category,
        Difficulty difficulty,
        RecipeStatus status,
        @Schema(description = "Numer bieżącej rewizji — odsyła się go przy zapisie, żeby wykryć równoległą edycję")
        int currentRevisionNo,
        Instant createdAt,
        Instant updatedAt,
        SourceResponse source,
        List<IngredientLineResponse> ingredients,
        List<StepResponse> steps,
        List<String> tags,
        List<String> diets) {

    }

    @Schema(description = "Przepis na liście")
    public record RecipeSummaryResponse(
        Long id,
        String title,
        String description,
        Integer totalMinutes,
        String cuisine,
        String category,
        Difficulty difficulty,
        int ingredientCount,
        int stepCount,
        Instant createdAt) {

    }

    @Schema(description = "Strona listy przepisów")
    public record RecipePageResponse(
        List<RecipeSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    }

    @Schema(description = "Skąd wziął się przepis")
    public record SourceResponse(SourceKind kind, String url, String siteName, String author) {

    }

    @Schema(description = "Składnik przepisu")
    public record IngredientLineResponse(
        Long id,
        int position,
        String group,
        @Schema(description = "Pozycja katalogu, do której dopasowano składnik; pusta = niedopasowany")
        Long ingredientId,
        String displayName,
        String sourceText,
        BigDecimal quantityMin,
        BigDecimal quantityMax,
        String unit,
        String unitName,
        String quantityText,
        String preparation,
        boolean optional,
        String note,
        List<AlternativeResponse> alternatives) {

    }

    @Schema(description = "Zamiennik składnika")
    public record AlternativeResponse(
        Long id,
        int position,
        Long ingredientId,
        String displayName,
        BigDecimal quantityMin,
        BigDecimal quantityMax,
        String unit,
        String unitName,
        String quantityText,
        String note) {

    }

    @Schema(description = "Krok przygotowania")
    public record StepResponse(
        Long id,
        int position,
        String group,
        String text,
        String sourceText,
        Integer durationMinutes,
        Integer temperatureC,
        String temperatureNote,
        @Schema(description = "Identyfikatory składników użytych w tym kroku")
        List<Long> ingredientIds,
        List<String> equipment) {

    }

    @Schema(description = "Uwaga do przepisu")
    public record NoteResponse(Long id, String body, Instant createdAt, Instant updatedAt) {

    }

    @Schema(description = "Wynik zapisu przepisu")
    public record SaveResponse(
        RecipeResponse recipe,
        @Schema(description = "Czy zapis coś zmienił — false znaczy, że rewizja nie powstała")
        boolean changed,
        int revisionNo) {

    }
}
