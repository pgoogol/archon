package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.api.IngredientDtos.AliasResponse;
import com.pgoogol.kitchen.api.IngredientDtos.IngredientPageResponse;
import com.pgoogol.kitchen.api.IngredientDtos.IngredientResponse;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.ingredient.domain.IngredientAlias;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Encje katalogu na odpowiedzi API. */
@Component
public class IngredientApiMapper {

    public IngredientResponse toResponse(Ingredient ingredient, List<IngredientAlias> aliases) {

        List<AliasResponse> mapped = aliases.stream().map(this::alias).toList();
        return new IngredientResponse(
            ingredient.getId(),
            ingredient.getName(),
            ingredient.getCategory(),
            codeOf(ingredient.getDefaultUnit()),
            ingredient.getStatus(),
            mapped);
    }

    public IngredientPageResponse toPage(Page<Ingredient> page) {

        List<IngredientResponse> items = page.getContent().stream()
            .map(ingredient -> toResponse(ingredient, List.of()))
            .toList();
        return new IngredientPageResponse(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }

    public AliasResponse alias(IngredientAlias alias) {

        return new AliasResponse(alias.getId(), alias.getAlias(), alias.getLanguage());
    }

    private String codeOf(Unit unit) {

        if (Objects.isNull(unit)) {

            return null;
        }
        return unit.getCode();
    }
}
