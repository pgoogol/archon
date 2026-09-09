package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.ExceptionMessageConstants;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeNote;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uwagi do przepisu — wolne notatki z gotowania: „za słone", „następnym razem
 * połowa cukru".
 *
 * <p>Nie są częścią treści przepisu i nie trafiają do dziennika zmian: notatka
 * opisuje wykonanie, a nie samą recepturę, i ma przeżyć każdą jej edycję.</p>
 */
@Service
@RequiredArgsConstructor
public class RecipeNoteService {

    private final RecipeNoteRepository notes;
    private final RecipeService recipes;

    @Transactional(readOnly = true)
    public List<RecipeNote> list(Long recipeId) {

        return notes.findByRecipeIdOrderByCreatedAtDesc(recipeId);
    }

    @Transactional
    public RecipeNote add(Long recipeId, String body) {

        Recipe recipe = recipes.get(recipeId);
        RecipeNote note = new RecipeNote(recipe, body);
        return notes.save(note);
    }

    @Transactional
    public RecipeNote edit(Long recipeId, Long noteId, String body) {

        RecipeNote note = load(recipeId, noteId);
        note.edit(body);
        return note;
    }

    @Transactional
    public void delete(Long recipeId, Long noteId) {

        RecipeNote note = load(recipeId, noteId);
        notes.delete(note);
    }

    private RecipeNote load(Long recipeId, Long noteId) {

        Optional<RecipeNote> found = notes.findById(noteId);
        RecipeNote note = found.orElseThrow(() -> new NotFoundException(ErrorCodes.NOTE_NOT_FOUND,
            ExceptionMessageConstants.NOTE_NOT_FOUND.formatted(noteId)));
        if (Objects.equals(note.getRecipe().getId(), recipeId)) {

            return note;
        }
        // uwaga spod innego przepisu jest dla tego adresu tym samym, co nieistniejąca
        throw new NotFoundException(ErrorCodes.NOTE_NOT_FOUND,
            ExceptionMessageConstants.NOTE_NOT_FOUND.formatted(noteId));
    }
}
