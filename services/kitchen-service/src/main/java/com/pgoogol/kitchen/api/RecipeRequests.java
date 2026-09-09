package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.recipe.domain.RecipeDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Żądania zapisu przepisu i uwag. */
public final class RecipeRequests {

    private RecipeRequests() {

    }

    /**
     * Zapis przepisu — ten sam kształt przy zakładaniu i przy edycji.
     *
     * @param expectedRevisionNo numer rewizji, który klient miał na ekranie.
     *                           Podany i nieaktualny kończy się konfliktem zamiast
     *                           cichego nadpisania cudzej zmiany; przy zakładaniu
     *                           przepisu nie ma znaczenia
     */
    @Schema(description = "Zapis przepisu: treść, opis zmiany i numer rewizji, na której pracował klient")
    public record SaveRecipeRequest(
        @NotNull @Valid RecipeDraft draft,
        @Size(max = 500) String changeSummary,
        Integer expectedRevisionNo) {

    }

    @Schema(description = "Treść uwagi do przepisu")
    public record NoteRequest(@NotBlank @Size(max = 5_000) String body) {

    }
}
