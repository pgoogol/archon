package com.pgoogol.kitchen.dictionary.application;

import com.pgoogol.kitchen.dictionary.domain.Cuisine;
import com.pgoogol.kitchen.dictionary.domain.Diet;
import com.pgoogol.kitchen.dictionary.domain.Equipment;
import com.pgoogol.kitchen.dictionary.domain.RecipeCategory;
import com.pgoogol.kitchen.dictionary.domain.Tag;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.dictionary.domain.UnitKind;
import com.pgoogol.kitchen.dictionary.infrastructure.CuisineRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.DietRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.EquipmentRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.RecipeCategoryRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.TagRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.UnitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    @Mock
    private CuisineRepository cuisines;

    @Mock
    private RecipeCategoryRepository categories;

    @Mock
    private DietRepository diets;

    @Mock
    private TagRepository tags;

    @Mock
    private EquipmentRepository equipment;

    @Mock
    private UnitRepository units;

    @InjectMocks
    private TermService service;

    @Test
    @DisplayName("findCuisine_whenNameHasDiacritics_matchesByNormalizedForm")
    void findCuisine_whenNameHasDiacritics_matchesByNormalizedForm() {

        // given: model językowy poda „Włoska", w słowniku leży postać porównawcza
        Cuisine italian = new Cuisine("włoska");
        given(cuisines.findByNameNormalized("wloska")).willReturn(Optional.of(italian));

        // when
        Optional<Cuisine> found = service.findCuisine("Włoska");

        // then
        assertThat(found).contains(italian);
    }

    @Test
    @DisplayName("findCuisine_whenNameBlank_returnsEmptyWithoutQuery")
    void findCuisine_whenNameBlank_returnsEmptyWithoutQuery() {

        // when
        Optional<Cuisine> found = service.findCuisine("  ");

        // then
        assertThat(found).isEmpty();
        verify(cuisines, never()).findByNameNormalized(any());
    }

    @Test
    @DisplayName("findCategory_whenNameKnown_returnsEntry")
    void findCategory_whenNameKnown_returnsEntry() {

        // given
        RecipeCategory main = new RecipeCategory("danie główne");
        given(categories.findByNameNormalized("danie glowne")).willReturn(Optional.of(main));

        // when
        Optional<RecipeCategory> found = service.findCategory("Danie Główne");

        // then
        assertThat(found).contains(main);
    }

    @Test
    @DisplayName("findDiets_whenNamesGiven_returnsOnlyKnownOnes")
    void findDiets_whenNamesGiven_returnsOnlyKnownOnes() {

        // given: dieta spoza słownika nie zakłada nowej pozycji — to zamknięta lista
        Diet vegetarian = new Diet("wegetariańska");
        given(diets.findByNameNormalizedIn(Set.of("wegetarianska", "paleo")))
            .willReturn(List.of(vegetarian));

        // when
        Set<Diet> found = service.findDiets(List.of("wegetariańska", "paleo"));

        // then
        assertThat(found).containsExactly(vegetarian);
    }

    @Test
    @DisplayName("findUnit_whenCodeGiven_looksUpByCode")
    void findUnit_whenCodeGiven_looksUpByCode() {

        // given
        Unit spoon = new Unit("lyzka", "łyżka", UnitKind.VOLUME, new BigDecimal("15"));
        given(units.findByCode("lyzka")).willReturn(Optional.of(spoon));

        // when
        Optional<Unit> found = service.findUnit(" lyzka ");

        // then
        assertThat(found).contains(spoon);
    }

    @Test
    @DisplayName("resolveTags_whenTagIsNew_createsItKeepingOriginalSpelling")
    void resolveTags_whenTagIsNew_createsItKeepingOriginalSpelling() {

        // given
        given(tags.findByNameNormalized("na swieta")).willReturn(Optional.empty());
        given(tags.save(any(Tag.class))).willAnswer(call -> call.getArgument(0));

        // when
        Set<Tag> resolved = service.resolveTags(List.of("Na Święta"));

        // then: w słowniku ma zostać pisownia użytkownika, nie postać porównawcza
        ArgumentCaptor<Tag> saved = ArgumentCaptor.forClass(Tag.class);
        verify(tags).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Na Święta");
        assertThat(saved.getValue().getNameNormalized()).isEqualTo("na swieta");
        assertThat(resolved).hasSize(1);
    }

    @Test
    @DisplayName("resolveTags_whenTagExists_reusesItInsteadOfDuplicating")
    void resolveTags_whenTagExists_reusesItInsteadOfDuplicating() {

        // given
        Tag existing = new Tag("na święta");
        given(tags.findByNameNormalized("na swieta")).willReturn(Optional.of(existing));

        // when
        Set<Tag> resolved = service.resolveTags(List.of("NA ŚWIĘTA"));

        // then
        assertThat(resolved).containsExactly(existing);
        verify(tags, never()).save(any());
    }

    @Test
    @DisplayName("resolveEquipment_whenNamesNull_returnsEmptySet")
    void resolveEquipment_whenNamesNull_returnsEmptySet() {

        // when
        Set<Equipment> resolved = service.resolveEquipment(null);

        // then
        assertThat(resolved).isEmpty();
        verify(equipment, never()).save(any());
    }
}
