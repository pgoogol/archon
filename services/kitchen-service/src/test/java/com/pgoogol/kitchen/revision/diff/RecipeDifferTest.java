package com.pgoogol.kitchen.revision.diff;

import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Header;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Ingredient;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Step;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.TermRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Sedno wersjonowania: historia ma mówić, CO się zmieniło. Podniesienie ilości
 * cebuli to jedna zmiana pola, a nie skasowanie wiersza i dodanie drugiego —
 * i to sprawdza większość przypadków niżej.
 */
class RecipeDifferTest {

    private final RecipeDiffer differ = new RecipeDiffer();

    @Test
    @DisplayName("diff_whenNothingChanged_returnsNoChanges")
    void diff_whenNothingChanged_returnsNoChanges() {

        // given
        RecipeSnapshot before = SnapshotFixtures.snapshot(
            List.of(SnapshotFixtures.ingredient(1L, 0, "cebula", "1")),
            List.of(SnapshotFixtures.step(10L, 0, "Posiekaj cebulę")));

        // when
        List<FieldChange> changes = differ.diff(before, before);

        // then
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("diff_whenQuantityRaised_recordsSingleFieldUpdate")
    void diff_whenQuantityRaised_recordsSingleFieldUpdate() {

        // given
        RecipeSnapshot before =
            SnapshotFixtures.snapshot(List.of(SnapshotFixtures.ingredient(1L, 0, "cebula", "1")), List.of());
        RecipeSnapshot after =
            SnapshotFixtures.snapshot(List.of(SnapshotFixtures.ingredient(1L, 0, "cebula", "2")), List.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then: dwa końce zakresu ilości, nic więcej — wiersz zostaje ten sam
        assertThat(changes).hasSize(2);
        assertThat(changes).allSatisfy(change -> {

            assertThat(change.operation()).isEqualTo(ChangeOperation.UPDATE);
            assertThat(change.targetType()).isEqualTo(TargetType.INGREDIENT);
            assertThat(change.targetId()).isEqualTo(1L);
            assertThat(change.targetLabel()).isEqualTo("cebula");
            assertThat(change.oldValue()).isEqualTo("1");
            assertThat(change.newValue()).isEqualTo("2");
        });
    }

    @Test
    @DisplayName("diff_whenQuantityDiffersOnlyByScale_recordsNothing")
    void diff_whenQuantityDiffersOnlyByScale_recordsNothing() {

        // given: baza potrafi oddać tę samą ilość z inną liczbą miejsc po przecinku
        RecipeSnapshot before = SnapshotFixtures.snapshot(
            List.of(SnapshotFixtures.ingredient(1L, 0, "cebula", "1")), List.of());
        RecipeSnapshot after = SnapshotFixtures.snapshot(
            List.of(SnapshotFixtures.ingredient(1L, 0, "cebula", "1.000")), List.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("diff_whenIngredientRemoved_keepsWholeRowForRestore")
    void diff_whenIngredientRemoved_keepsWholeRowForRestore() {

        // given
        Ingredient onion = SnapshotFixtures.ingredient(1L, 0, "cebula", "1");
        Ingredient coriander = SnapshotFixtures.ingredient(2L, 1, "kolendra", "1");
        RecipeSnapshot before = SnapshotFixtures.snapshot(List.of(onion, coriander), List.of());
        RecipeSnapshot after = SnapshotFixtures.snapshot(List.of(onion), List.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then: bez pełnej treści wiersza nie dałoby się cofnąć usunięcia
        assertThat(changes).hasSize(1);
        FieldChange change = changes.getFirst();
        assertThat(change.operation()).isEqualTo(ChangeOperation.REMOVE);
        assertThat(change.targetLabel()).isEqualTo("kolendra");
        assertThat(change.removedRow()).isEqualTo(coriander);
    }

    @Test
    @DisplayName("diff_whenIngredientAdded_recordsAddWithoutRow")
    void diff_whenIngredientAdded_recordsAddWithoutRow() {

        // given
        Ingredient onion = SnapshotFixtures.ingredient(1L, 0, "cebula", "1");
        Ingredient garlic = SnapshotFixtures.ingredient(3L, 1, "czosnek", "2");
        RecipeSnapshot before = SnapshotFixtures.snapshot(List.of(onion), List.of());
        RecipeSnapshot after = SnapshotFixtures.snapshot(List.of(onion, garlic), List.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then: cofnięcie dodania to skasowanie wiersza — treść jest niepotrzebna
        assertThat(changes).hasSize(1);
        FieldChange change = changes.getFirst();
        assertThat(change.operation()).isEqualTo(ChangeOperation.ADD);
        assertThat(change.targetId()).isEqualTo(3L);
        assertThat(change.removedRow()).isNull();
    }

    @Test
    @DisplayName("diff_whenStepsReordered_recordsMoveInsteadOfRemoveAndAdd")
    void diff_whenStepsReordered_recordsMoveInsteadOfRemoveAndAdd() {

        // given
        RecipeSnapshot before = SnapshotFixtures.snapshot(List.of(),
            List.of(SnapshotFixtures.step(10L, 0, "Podgrzej piekarnik"),
                SnapshotFixtures.step(11L, 1, "Zmiksuj masę")));
        RecipeSnapshot after = SnapshotFixtures.snapshot(List.of(),
            List.of(SnapshotFixtures.step(11L, 0, "Zmiksuj masę"),
                SnapshotFixtures.step(10L, 1, "Podgrzej piekarnik")));

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then
        assertThat(changes).hasSize(2);
        assertThat(changes).allMatch(change -> change.operation() == ChangeOperation.MOVE);
        assertThat(changes).allMatch(change -> "position".equals(change.field()));
    }

    @Test
    @DisplayName("diff_whenTitleChanged_recordsHeaderUpdate")
    void diff_whenTitleChanged_recordsHeaderUpdate() {

        // given
        RecipeSnapshot before =
            SnapshotFixtures.snapshot(SnapshotFixtures.header("Sernik"), List.of(), List.of());
        RecipeSnapshot after =
            SnapshotFixtures.snapshot(SnapshotFixtures.header("Sernik wiedeński"), List.of(), List.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then
        assertThat(changes).hasSize(1);
        FieldChange change = changes.getFirst();
        assertThat(change.targetType()).isEqualTo(TargetType.RECIPE);
        assertThat(change.field()).isEqualTo("title");
        assertThat(change.oldValue()).isEqualTo("Sernik");
        assertThat(change.newValue()).isEqualTo("Sernik wiedeński");
    }

    @Test
    @DisplayName("diff_whenCuisineCleared_recordsUpdateWithOldName")
    void diff_whenCuisineCleared_recordsUpdateWithOldName() {

        // given
        Header withCuisine = SnapshotFixtures.header("Sernik");
        Header withoutCuisine = new Header("Sernik", "opis", new BigDecimal("4"), "porcje",
            15, 30, 45, null, new TermRef(2L, "danie główne"), "EASY");
        RecipeSnapshot before = SnapshotFixtures.snapshot(withCuisine, List.of(), List.of());
        RecipeSnapshot after = SnapshotFixtures.snapshot(withoutCuisine, List.of(), List.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then: w historii ma zostać nazwa, a nie identyfikator z bazy
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().oldValue()).isEqualTo("polska");
        assertThat(changes.getFirst().newValue()).isNull();
    }

    @Test
    @DisplayName("diff_whenTagAttached_recordsTagAdd")
    void diff_whenTagAttached_recordsTagAdd() {

        // given
        Header header = SnapshotFixtures.header("Sernik");
        RecipeSnapshot before = new RecipeSnapshot(header, List.of(), List.of(), Set.of(), Set.of());
        RecipeSnapshot after = new RecipeSnapshot(header, List.of(), List.of(),
            Set.of(new TermRef(7L, "na święta")), Set.of());

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then
        assertThat(changes).hasSize(1);
        FieldChange change = changes.getFirst();
        assertThat(change.targetType()).isEqualTo(TargetType.TAG);
        assertThat(change.operation()).isEqualTo(ChangeOperation.ADD);
        assertThat(change.targetLabel()).isEqualTo("na święta");
    }

    @Test
    @DisplayName("diff_whenStepGainsIngredientLink_recordsLinkAdd")
    void diff_whenStepGainsIngredientLink_recordsLinkAdd() {

        // given
        Step without = SnapshotFixtures.step(10L, 0, "Posiekaj cebulę");
        Step with = new Step(10L, 0, null, "Posiekaj cebulę", null, null, null, null,
            Set.of(1L), Set.of());
        RecipeSnapshot before = SnapshotFixtures.snapshot(List.of(), List.of(without));
        RecipeSnapshot after = SnapshotFixtures.snapshot(List.of(), List.of(with));

        // when
        List<FieldChange> changes = differ.diff(before, after);

        // then
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().targetType()).isEqualTo(TargetType.STEP_INGREDIENT);
        assertThat(changes.getFirst().operation()).isEqualTo(ChangeOperation.ADD);
    }

    @Test
    @DisplayName("diff_whenSnapshotIsNull_failsFast")
    void diff_whenSnapshotIsNull_failsFast() {

        // given
        RecipeSnapshot snapshot = SnapshotFixtures.snapshot(List.of(), List.of());

        // when & then
        assertThatCode(() -> differ.diff(null, snapshot)).isInstanceOf(NullPointerException.class);
    }
}
