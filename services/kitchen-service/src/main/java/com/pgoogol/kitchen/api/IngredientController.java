package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.api.IngredientDtos.AliasRequest;
import com.pgoogol.kitchen.api.IngredientDtos.AliasResponse;
import com.pgoogol.kitchen.api.IngredientDtos.IngredientPageResponse;
import com.pgoogol.kitchen.api.IngredientDtos.IngredientRequest;
import com.pgoogol.kitchen.api.IngredientDtos.IngredientResponse;
import com.pgoogol.kitchen.ingredient.application.IngredientService;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.ingredient.domain.IngredientAlias;
import com.pgoogol.kitchen.ingredient.domain.IngredientStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Katalog składników. Rośnie sam z importów i formularza — ten ekran służy do
 * jego porządkowania: poprawienia nazwy, podpięcia synonimu, potwierdzenia
 * pozycji dopisanej automatycznie.
 */
@RestController
@RequestMapping("/kitchen/api/v1/ingredients")
@RequiredArgsConstructor
@Tag(name = "ingredients", description = "Katalog składników i synonimy")
public class IngredientController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final IngredientService ingredients;
    private final IngredientApiMapper mapper;

    @GetMapping
    @Operation(summary = "Katalog z filtrem po nazwie i statusie")
    public IngredientPageResponse search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) IngredientStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), cappedSize(size), Sort.by("name"));
        Page<Ingredient> found = ingredients.search(q, status, pageable);
        return mapper.toPage(found);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pozycja katalogu razem z synonimami")
    public IngredientResponse get(@PathVariable Long id) {

        Ingredient ingredient = ingredients.get(id);
        List<IngredientAlias> aliases = ingredients.aliasesOf(id);
        return mapper.toResponse(ingredient, aliases);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa pozycja katalogu")
    public IngredientResponse create(@Valid @RequestBody IngredientRequest request) {

        Ingredient ingredient =
            ingredients.create(request.name(), request.category(), request.defaultUnit());
        return mapper.toResponse(ingredient, List.of());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Poprawienie nazwy, kategorii, jednostki albo statusu")
    public IngredientResponse update(@PathVariable Long id, @Valid @RequestBody IngredientRequest request) {

        Ingredient ingredient = ingredients.update(
            id, request.name(), request.category(), request.defaultUnit(), request.status());
        List<IngredientAlias> aliases = ingredients.aliasesOf(id);
        return mapper.toResponse(ingredient, aliases);
    }

    @PostMapping("/{id}/aliases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowy synonim prowadzący do tej pozycji")
    public AliasResponse addAlias(@PathVariable Long id, @Valid @RequestBody AliasRequest request) {

        IngredientAlias alias = ingredients.addAlias(id, request.alias(), request.language());
        return mapper.alias(alias);
    }

    @DeleteMapping("/{id}/aliases/{aliasId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usunięcie synonimu")
    public void deleteAlias(@PathVariable Long id, @PathVariable Long aliasId) {

        ingredients.deleteAlias(id, aliasId);
    }

    private int cappedSize(int size) {

        if (size < 1) {

            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
