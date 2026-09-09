package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.common.ConflictException;
import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.common.ValidationException;
import com.pgoogol.kitchen.recipe.RecipeFixtures;
import com.pgoogol.kitchen.recipe.application.RecipeWriter.WriteResult;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeDraft;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeRepository;
import com.pgoogol.kitchen.revision.application.RevisionRecorder;
import com.pgoogol.kitchen.revision.diff.FieldChange;
import com.pgoogol.kitchen.revision.diff.RecipeDiffer;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot;
import com.pgoogol.kitchen.revision.diff.RevisionOrigin;
import com.pgoogol.kitchen.revision.diff.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Jedyna droga zapisu przepisu — i jedyne miejsce, w którym powstaje wpis
 * w dzienniku. Testy pilnują trzech rzeczy: że rewizja powstaje, gdy coś się
 * zmieniło, że NIE powstaje, gdy nic się nie zmieniło, i że równoległa edycja
 * kończy się konfliktem zamiast cichego nadpisania.
 */
@ExtendWith(MockitoExtension.class)
class RecipeWriterTest {

    @Mock
    private RecipeRepository recipes;

    @Mock
    private RecipeMutator mutator;

    @Mock
    private RecipeSnapshots snapshots;

    @Mock
    private RecipeDiffer differ;

    @Mock
    private RevisionRecorder revisions;

    @InjectMocks
    private RecipeWriter writer;

    @Test
    @DisplayName("create_whenDraftHasContent_recordsFirstRevision")
    void create_whenDraftHasContent_recordsFirstRevision() {

        // given
        RecipeDraft draft = RecipeFixtures.draft("Sernik");
        given(recipes.save(any(Recipe.class))).willAnswer(call -> call.getArgument(0));

        // when
        WriteResult result = writer.create(draft, RevisionOrigin.MANUAL, "z formularza", null);

        // then: historia zaczyna się jednym wpisem „utworzono", a nie listą pól z pustki
        assertThat(result.changed()).isTrue();
        verify(revisions).recordCreation(any(Recipe.class), eq(RevisionOrigin.MANUAL), eq("z formularza"));
        verify(mutator).apply(any(Recipe.class), eq(draft));
    }

    @Test
    @DisplayName("create_whenDraftHasNeitherIngredientsNorSteps_throwsValidation")
    void create_whenDraftHasNeitherIngredientsNorSteps_throwsValidation() {

        // given
        RecipeDraft empty = RecipeFixtures.emptyDraft("Pusty");

        // when & then
        assertThatThrownBy(() -> writer.create(empty, RevisionOrigin.MANUAL, null, null))
            .isInstanceOf(ValidationException.class)
            .extracting(exception -> ((ValidationException) exception).getErrorCode())
            .isEqualTo(ErrorCodes.RECIPE_EMPTY);
        verify(recipes, never()).save(any());
    }

    @Test
    @DisplayName("save_whenSomethingChanged_recordsRevisionWithChanges")
    void save_whenSomethingChanged_recordsRevisionWithChanges() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        RecipeSnapshot snapshot = emptySnapshot();
        FieldChange change = FieldChange.updated(TargetType.RECIPE, null, "Sernik",
            "title", "Sernik", "Sernik wiedeński");
        given(recipes.findWithContentById(1L)).willReturn(Optional.of(recipe));
        given(snapshots.of(recipe)).willReturn(snapshot);
        given(differ.diff(snapshot, snapshot)).willReturn(List.of(change));

        // when
        WriteResult result = writer.save(1L, RecipeFixtures.draft("Sernik wiedeński"),
            RevisionOrigin.MANUAL, "poprawka tytułu", null);

        // then
        assertThat(result.changed()).isTrue();
        assertThat(result.changes()).containsExactly(change);
        verify(revisions).record(recipe, List.of(change), RevisionOrigin.MANUAL, "poprawka tytułu");
    }

    @Test
    @DisplayName("save_whenNothingChanged_skipsRevision")
    void save_whenNothingChanged_skipsRevision() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        RecipeSnapshot snapshot = emptySnapshot();
        given(recipes.findWithContentById(1L)).willReturn(Optional.of(recipe));
        given(snapshots.of(recipe)).willReturn(snapshot);
        given(differ.diff(snapshot, snapshot)).willReturn(List.of());

        // when
        WriteResult result = writer.save(1L, RecipeFixtures.draft("Sernik"),
            RevisionOrigin.MANUAL, null, null);

        // then: historia ma mówić o zmianach, a nie o klikaniu „zapisz"
        assertThat(result.changed()).isFalse();
        verify(revisions, never()).record(any(), anyList(), any(), anyString());
    }

    @Test
    @DisplayName("save_whenExpectedRevisionIsStale_throwsConflictBeforeTouchingRecipe")
    void save_whenExpectedRevisionIsStale_throwsConflictBeforeTouchingRecipe() {

        // given: klient miał na ekranie rewizję 3, w bazie jest już 4
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        recipe.nextRevisionNo();
        given(recipes.findWithContentById(1L)).willReturn(Optional.of(recipe));

        // when & then
        assertThatThrownBy(() -> writer.save(1L, RecipeFixtures.draft("Sernik"),
            RevisionOrigin.MANUAL, null, 3))
            .isInstanceOf(ConflictException.class)
            .extracting(exception -> ((ConflictException) exception).getErrorCode())
            .isEqualTo(ErrorCodes.RECIPE_MODIFIED);
        verify(mutator, never()).apply(any(), any());
    }

    @Test
    @DisplayName("save_whenRecipeMissing_throwsNotFound")
    void save_whenRecipeMissing_throwsNotFound() {

        // given
        given(recipes.findWithContentById(7L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> writer.save(7L, RecipeFixtures.draft("Sernik"),
            RevisionOrigin.MANUAL, null, null))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("save_whenRecipeArchived_throwsConflict")
    void save_whenRecipeArchived_throwsConflict() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        recipe.archive();
        given(recipes.findWithContentById(1L)).willReturn(Optional.of(recipe));

        // when & then
        assertThatThrownBy(() -> writer.save(1L, RecipeFixtures.draft("Sernik"),
            RevisionOrigin.MANUAL, null, null))
            .isInstanceOf(ConflictException.class)
            .extracting(exception -> ((ConflictException) exception).getErrorCode())
            .isEqualTo(ErrorCodes.RECIPE_ARCHIVED);
    }

    private RecipeSnapshot emptySnapshot() {

        RecipeSnapshot.Header header =
            new RecipeSnapshot.Header(null, null, null, null, null, null, null, null, null, null);
        return new RecipeSnapshot(header, List.of(), List.of(), Set.of(), Set.of());
    }
}
