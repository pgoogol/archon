package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.common.ConflictException;
import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.ExceptionMessageConstants;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.common.ValidationException;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft;
import com.pgoogol.kitchen.recipe.domain.RecipeSource;
import com.pgoogol.kitchen.recipe.domain.RecipeStatus;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeRepository;
import com.pgoogol.kitchen.revision.application.RevisionRecorder;
import com.pgoogol.kitchen.revision.diff.FieldChange;
import com.pgoogol.kitchen.revision.diff.RecipeDiffer;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot;
import com.pgoogol.kitchen.revision.diff.RevisionOrigin;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Jedyna droga, którą przepis trafia do bazy.
 *
 * <p>Wchodzą tędy: formularz (nowy przepis i edycja), akceptacja szkicu
 * z importu, przepis z czatu i przywrócenie starszej wersji. Jedno wejście
 * znaczy jeden zestaw reguł — a przy okazji jedno miejsce, w którym powstaje
 * wpis w dzienniku zmian.</p>
 *
 * <p>Zapis, który niczego nie zmienił, NIE tworzy rewizji: historia ma mówić
 * o zmianach, a nie o tym, ile razy ktoś kliknął „zapisz".</p>
 */
@Service
@RequiredArgsConstructor
public class RecipeWriter {

    private final RecipeRepository recipes;
    private final RecipeMutator mutator;
    private final RecipeSnapshots snapshots;
    private final RecipeDiffer differ;
    private final RevisionRecorder revisions;

    @Transactional
    public WriteResult create(RecipeDraft draft, RevisionOrigin origin, String changeSummary,
                              RecipeSource source) {

        requireContent(draft);
        Recipe recipe = new Recipe(draft.title().trim());
        recipe.setSource(source);
        recipes.save(recipe);
        mutator.apply(recipe, draft);
        recipes.flush();
        revisions.recordCreation(recipe, origin, changeSummary);
        return new WriteResult(recipe, true, recipe.getCurrentRevisionNo(), List.of());
    }

    @Transactional
    public WriteResult save(Long recipeId, RecipeDraft draft, RevisionOrigin origin,
                            String changeSummary, Integer expectedRevisionNo) {

        requireContent(draft);
        Recipe recipe = load(recipeId);
        requireActive(recipe);
        requireCurrent(recipe, expectedRevisionNo);
        RecipeSnapshot before = snapshots.of(recipe);
        mutator.apply(recipe, draft);
        recipes.flush();
        RecipeSnapshot after = snapshots.of(recipe);
        List<FieldChange> changes = differ.diff(before, after);
        if (changes.isEmpty()) {

            return new WriteResult(recipe, false, recipe.getCurrentRevisionNo(), List.of());
        }
        revisions.record(recipe, changes, origin, changeSummary);
        return new WriteResult(recipe, true, recipe.getCurrentRevisionNo(), changes);
    }

    private Recipe load(Long recipeId) {

        Optional<Recipe> recipe = recipes.findWithContentById(recipeId);
        return recipe.orElseThrow(() -> new NotFoundException(ErrorCodes.RECIPE_NOT_FOUND,
            ExceptionMessageConstants.RECIPE_NOT_FOUND.formatted(recipeId)));
    }

    private void requireActive(Recipe recipe) {

        if (recipe.getStatus() != RecipeStatus.ARCHIVED) {

            return;
        }
        throw new ConflictException(ErrorCodes.RECIPE_ARCHIVED,
            ExceptionMessageConstants.RECIPE_ARCHIVED.formatted(recipe.getId()));
    }

    /**
     * Numer rewizji, który klient miał na ekranie, kontra ten w bazie. Bez tego
     * dwie karty otwarte na tym samym przepisie po cichu nadpisywałyby się
     * nawzajem — a ostatni zapis wygrywałby bez śladu, że coś przepadło.
     */
    private void requireCurrent(Recipe recipe, Integer expectedRevisionNo) {

        if (Objects.isNull(expectedRevisionNo) || expectedRevisionNo == recipe.getCurrentRevisionNo()) {

            return;
        }
        throw new ConflictException(ErrorCodes.RECIPE_MODIFIED,
            ExceptionMessageConstants.RECIPE_MODIFIED);
    }

    private void requireContent(RecipeDraft draft) {

        if (CollectionUtils.isNotEmpty(draft.ingredients())
            || CollectionUtils.isNotEmpty(draft.steps())) {

            return;
        }
        throw new ValidationException(ErrorCodes.RECIPE_EMPTY, ExceptionMessageConstants.RECIPE_EMPTY);
    }

    /**
     * @param changed czy zapis w ogóle coś zmienił — {@code false} znaczy, że
     *                rewizja nie powstała
     */
    public record WriteResult(Recipe recipe, boolean changed, int revisionNo, List<FieldChange> changes) {

    }
}
