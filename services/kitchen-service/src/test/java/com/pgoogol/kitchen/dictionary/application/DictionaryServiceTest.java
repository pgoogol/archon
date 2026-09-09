package com.pgoogol.kitchen.dictionary.application;

import com.pgoogol.kitchen.dictionary.domain.Cuisine;
import com.pgoogol.kitchen.dictionary.domain.Dictionaries;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceTest {

    @Mock
    private UnitRepository units;

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

    @InjectMocks
    private DictionaryService service;

    @Test
    @DisplayName("all_whenDictionariesHaveEntries_returnsThemInOneAnswer")
    void all_whenDictionariesHaveEntries_returnsThemInOneAnswer() {

        // given
        given(units.findAllByOrderByIdAsc())
            .willReturn(List.of(new Unit("g", "gram", UnitKind.MASS, BigDecimal.ONE)));
        given(cuisines.findAllByOrderByNameAsc()).willReturn(List.of(new Cuisine("polska")));
        given(categories.findAllByOrderByNameAsc()).willReturn(List.of(new RecipeCategory("zupa")));
        given(diets.findAllByOrderByNameAsc()).willReturn(List.of(new Diet("wegetariańska")));
        given(tags.findAllByOrderByNameAsc()).willReturn(List.of(new Tag("na święta")));
        given(equipment.findAllByOrderByNameAsc()).willReturn(List.of(new Equipment("piekarnik")));

        // when
        Dictionaries dictionaries = service.all();

        // then
        assertThat(dictionaries.units()).hasSize(1);
        assertThat(dictionaries.cuisines()).singleElement()
            .extracting(entry -> entry.name()).isEqualTo("polska");
        assertThat(dictionaries.categories()).hasSize(1);
        assertThat(dictionaries.diets()).hasSize(1);
        assertThat(dictionaries.tags()).hasSize(1);
        assertThat(dictionaries.equipment()).hasSize(1);
    }

    @Test
    @DisplayName("all_whenDictionariesEmpty_returnsEmptyListsInsteadOfNulls")
    void all_whenDictionariesEmpty_returnsEmptyListsInsteadOfNulls() {

        // given
        given(units.findAllByOrderByIdAsc()).willReturn(List.of());
        given(cuisines.findAllByOrderByNameAsc()).willReturn(List.of());
        given(categories.findAllByOrderByNameAsc()).willReturn(List.of());
        given(diets.findAllByOrderByNameAsc()).willReturn(List.of());
        given(tags.findAllByOrderByNameAsc()).willReturn(List.of());
        given(equipment.findAllByOrderByNameAsc()).willReturn(List.of());

        // when
        Dictionaries dictionaries = service.all();

        // then: front ma dostać puste listy, a nie ekran, który się nie ładuje
        assertThat(dictionaries.units()).isEmpty();
        assertThat(dictionaries.tags()).isEmpty();
    }
}
