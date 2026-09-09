package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.ingredient.domain.IngredientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Żądania i odpowiedzi katalogu składników. */
public final class IngredientDtos {

    private IngredientDtos() {

    }

    @Schema(description = "Pozycja katalogu składników")
    public record IngredientResponse(
        Long id,
        String name,
        String category,
        @Schema(description = "Kod jednostki, w której zwykle podaje się ten składnik")
        String defaultUnit,
        IngredientStatus status,
        List<AliasResponse> aliases) {

    }

    @Schema(description = "Synonim prowadzący do pozycji katalogu")
    public record AliasResponse(Long id, String alias, String language) {

    }

    @Schema(description = "Strona katalogu składników")
    public record IngredientPageResponse(
        List<IngredientResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    }

    @Schema(description = "Nowa albo zmieniona pozycja katalogu")
    public record IngredientRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 50) String category,
        @Size(max = 30) String defaultUnit,
        IngredientStatus status) {

    }

    @Schema(description = "Nowy synonim")
    public record AliasRequest(
        @NotBlank @Size(max = 200) String alias,
        @Size(max = 5) String language) {

    }
}
