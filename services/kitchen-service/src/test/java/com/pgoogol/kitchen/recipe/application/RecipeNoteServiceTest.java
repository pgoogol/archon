package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.recipe.RecipeFixtures;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeNote;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeNoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecipeNoteServiceTest {

    @Mock
    private RecipeNoteRepository notes;

    @Mock
    private RecipeService recipes;

    @InjectMocks
    private RecipeNoteService service;

    @Test
    @DisplayName("list_whenRecipeHasNotes_returnsThemNewestFirst")
    void list_whenRecipeHasNotes_returnsThemNewestFirst() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        RecipeNote note = new RecipeNote(recipe, "za słone");
        given(notes.findByRecipeIdOrderByCreatedAtDesc(1L)).willReturn(List.of(note));

        // when
        List<RecipeNote> found = service.list(1L);

        // then
        assertThat(found).singleElement().extracting(RecipeNote::getBody).isEqualTo("za słone");
    }

    @Test
    @DisplayName("add_whenRecipeExists_savesNoteWithBody")
    void add_whenRecipeExists_savesNoteWithBody() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        given(recipes.get(1L)).willReturn(recipe);
        given(notes.save(any(RecipeNote.class))).willAnswer(call -> call.getArgument(0));

        // when
        service.add(1L, "następnym razem połowa cukru");

        // then
        ArgumentCaptor<RecipeNote> saved = ArgumentCaptor.forClass(RecipeNote.class);
        verify(notes).save(saved.capture());
        assertThat(saved.getValue().getBody()).isEqualTo("następnym razem połowa cukru");
    }

    @Test
    @DisplayName("edit_whenNoteBelongsToRecipe_changesBody")
    void edit_whenNoteBelongsToRecipe_changesBody() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        RecipeNote note = RecipeFixtures.withId(new RecipeNote(recipe, "za słone"), 5L);
        given(notes.findById(5L)).willReturn(Optional.of(note));

        // when
        RecipeNote edited = service.edit(1L, 5L, "w sam raz");

        // then
        assertThat(edited.getBody()).isEqualTo("w sam raz");
    }

    @Test
    @DisplayName("edit_whenNoteBelongsToAnotherRecipe_throwsNotFound")
    void edit_whenNoteBelongsToAnotherRecipe_throwsNotFound() {

        // given: uwaga spod cudzego przepisu jest dla tego adresu nieistniejąca
        Recipe other = RecipeFixtures.recipe(2L, "Rosół");
        RecipeNote note = RecipeFixtures.withId(new RecipeNote(other, "za słone"), 5L);
        given(notes.findById(5L)).willReturn(Optional.of(note));

        // when & then
        assertThatThrownBy(() -> service.edit(1L, 5L, "w sam raz"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("delete_whenNoteMissing_throwsNotFoundAndDeletesNothing")
    void delete_whenNoteMissing_throwsNotFoundAndDeletesNothing() {

        // given
        given(notes.findById(9L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.delete(1L, 9L)).isInstanceOf(NotFoundException.class);
        verify(notes, never()).delete(any());
    }

    @Test
    @DisplayName("delete_whenNoteBelongsToRecipe_removesIt")
    void delete_whenNoteBelongsToRecipe_removesIt() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        RecipeNote note = RecipeFixtures.withId(new RecipeNote(recipe, "za słone"), 5L);
        given(notes.findById(5L)).willReturn(Optional.of(note));

        // when
        service.delete(1L, 5L);

        // then
        verify(notes).delete(note);
    }
}
