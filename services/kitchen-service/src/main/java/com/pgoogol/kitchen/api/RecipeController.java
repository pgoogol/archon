package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.api.RecipeRequests.SaveRecipeRequest;
import com.pgoogol.kitchen.api.RecipeResponses.RecipePageResponse;
import com.pgoogol.kitchen.api.RecipeResponses.RecipeResponse;
import com.pgoogol.kitchen.api.RecipeResponses.SaveResponse;
import com.pgoogol.kitchen.recipe.application.RecipeService;
import com.pgoogol.kitchen.recipe.application.RecipeWriter;
import com.pgoogol.kitchen.recipe.application.RecipeWriter.WriteResult;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeSource;
import com.pgoogol.kitchen.recipe.domain.SourceKind;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeSourceRepository;
import com.pgoogol.kitchen.revision.diff.RevisionOrigin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Przepisy: lista, odczyt, zapis i archiwizacja.
 *
 * <p>Zapis idzie przez {@link RecipeWriter} — jedyną drogę, którą treść przepisu
 * trafia do bazy, i jedyne miejsce, w którym powstaje wpis w dzienniku zmian.</p>
 */
@RestController
@RequestMapping("/kitchen/api/v1/recipes")
@RequiredArgsConstructor
@Tag(name = "recipes", description = "Przepisy: lista, odczyt, zapis, archiwizacja")
public class RecipeController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final RecipeService recipeService;
    private final RecipeWriter recipeWriter;
    private final RecipeSourceRepository sources;
    private final RecipeApiMapper mapper;

    @GetMapping
    @Operation(summary = "Lista przepisów od najnowszego")
    public RecipePageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), cappedSize(size));
        Page<Recipe> recipes = recipeService.list(pageable);
        return mapper.toPage(recipes);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Przepis z całą zawartością")
    public RecipeResponse get(@PathVariable Long id) {

        Recipe recipe = recipeService.get(id);
        return mapper.toResponse(recipe);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowy przepis wpisany ręcznie")
    public SaveResponse create(@Valid @RequestBody SaveRecipeRequest request) {

        RecipeSource source = sources.save(new RecipeSource(SourceKind.MANUAL));
        WriteResult result = recipeWriter.create(
            request.draft(), RevisionOrigin.MANUAL, request.changeSummary(), source);
        return toSaveResponse(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zapis zmian — tworzy rewizję tylko wtedy, gdy coś naprawdę się zmieniło")
    public SaveResponse update(@PathVariable Long id, @Valid @RequestBody SaveRecipeRequest request) {

        WriteResult result = recipeWriter.save(id, request.draft(), RevisionOrigin.MANUAL,
            request.changeSummary(), request.expectedRevisionNo());
        return toSaveResponse(result);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archiwizacja przepisu — dziennik zmian zachowuje odniesienia")
    public void archive(@PathVariable Long id) {

        recipeService.archive(id);
    }

    private SaveResponse toSaveResponse(WriteResult result) {

        RecipeResponse response = mapper.toResponse(result.recipe());
        return new SaveResponse(response, result.changed(), result.revisionNo());
    }

    private int cappedSize(int size) {

        if (size < 1) {

            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
