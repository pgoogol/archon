package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.api.RecipeRequests.NoteRequest;
import com.pgoogol.kitchen.api.RecipeResponses.NoteResponse;
import com.pgoogol.kitchen.recipe.application.RecipeNoteService;
import com.pgoogol.kitchen.recipe.domain.RecipeNote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Uwagi do przepisu — notatki z gotowania. Nie są częścią receptury, więc nie
 * przechodzą przez dziennik zmian i przeżywają każdą jej edycję.
 */
@RestController
@RequestMapping("/kitchen/api/v1/recipes/{recipeId}/notes")
@RequiredArgsConstructor
@Tag(name = "notes", description = "Uwagi do przepisu")
public class RecipeNoteController {

    private final RecipeNoteService notes;
    private final RecipeApiMapper mapper;

    @GetMapping
    @Operation(summary = "Uwagi od najnowszej")
    public List<NoteResponse> list(@PathVariable Long recipeId) {

        List<RecipeNote> found = notes.list(recipeId);
        return found.stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa uwaga")
    public NoteResponse add(@PathVariable Long recipeId, @Valid @RequestBody NoteRequest request) {

        RecipeNote note = notes.add(recipeId, request.body());
        return mapper.toResponse(note);
    }

    @PatchMapping("/{noteId}")
    @Operation(summary = "Zmiana treści uwagi")
    public NoteResponse edit(@PathVariable Long recipeId, @PathVariable Long noteId,
                             @Valid @RequestBody NoteRequest request) {

        RecipeNote note = notes.edit(recipeId, noteId, request.body());
        return mapper.toResponse(note);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usunięcie uwagi")
    public void delete(@PathVariable Long recipeId, @PathVariable Long noteId) {

        notes.delete(recipeId, noteId);
    }
}
