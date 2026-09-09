package com.pgoogol.kitchen.ingredient.application;

import com.pgoogol.kitchen.common.ConflictException;
import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.dictionary.application.TermService;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.dictionary.domain.UnitKind;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.ingredient.domain.IngredientAlias;
import com.pgoogol.kitchen.ingredient.domain.IngredientStatus;
import com.pgoogol.kitchen.ingredient.infrastructure.IngredientAliasRepository;
import com.pgoogol.kitchen.ingredient.infrastructure.IngredientRepository;
import com.pgoogol.kitchen.recipe.RecipeFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private IngredientRepository ingredients;

    @Mock
    private IngredientAliasRepository aliases;

    @Mock
    private TermService terms;

    @InjectMocks
    private IngredientService service;

    @Test
    @DisplayName("search_whenQueryGiven_looksUpByNormalizedFragment")
    void search_whenQueryGiven_looksUpByNormalizedFragment() {

        // given: „Cukinia" i „cukinia" mają trafiać w to samo
        Page<Ingredient> page = new PageImpl<>(List.of(new Ingredient("cukinia", IngredientStatus.VERIFIED)));
        given(ingredients.findByNameNormalizedContaining("cukinia", PAGE)).willReturn(page);

        // when
        Page<Ingredient> found = service.search("Cukinia", null, PAGE);

        // then
        assertThat(found.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("search_whenOnlyStatusGiven_filtersByStatus")
    void search_whenOnlyStatusGiven_filtersByStatus() {

        // given
        Page<Ingredient> page = new PageImpl<>(List.of());
        given(ingredients.findByStatus(IngredientStatus.NEW, PAGE)).willReturn(page);

        // when
        service.search(null, IngredientStatus.NEW, PAGE);

        // then
        verify(ingredients).findByStatus(IngredientStatus.NEW, PAGE);
    }

    @Test
    @DisplayName("search_whenNoFilters_returnsWholeCatalog")
    void search_whenNoFilters_returnsWholeCatalog() {

        // given
        given(ingredients.findAll(PAGE)).willReturn(new PageImpl<>(List.of()));

        // when
        service.search(" ", null, PAGE);

        // then
        verify(ingredients).findAll(PAGE);
    }

    @Test
    @DisplayName("create_whenNameFree_savesVerifiedEntry")
    void create_whenNameFree_savesVerifiedEntry() {

        // given
        Unit gram = new Unit("g", "gram", UnitKind.MASS, BigDecimal.ONE);
        given(ingredients.findByNameNormalized("cebula")).willReturn(Optional.empty());
        given(terms.findUnit("g")).willReturn(Optional.of(gram));
        given(ingredients.save(any(Ingredient.class))).willAnswer(call -> call.getArgument(0));

        // when
        Ingredient created = service.create("Cebula", "warzywa", "g");

        // then: pozycja dopisana ręcznie jest od razu potwierdzona
        assertThat(created.getStatus()).isEqualTo(IngredientStatus.VERIFIED);
        assertThat(created.getDefaultUnit()).isEqualTo(gram);
    }

    @Test
    @DisplayName("create_whenNameTaken_throwsConflict")
    void create_whenNameTaken_throwsConflict() {

        // given
        Ingredient existing = new Ingredient("cebula", IngredientStatus.VERIFIED);
        given(ingredients.findByNameNormalized("cebula")).willReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> service.create("Cebula", null, null))
            .isInstanceOf(ConflictException.class)
            .extracting(exception -> ((ConflictException) exception).getErrorCode())
            .isEqualTo(ErrorCodes.INGREDIENT_EXISTS);
        verify(ingredients, never()).save(any());
    }

    @Test
    @DisplayName("update_whenNameGiven_renamesAndKeepsNormalizedFormInSync")
    void update_whenNameGiven_renamesAndKeepsNormalizedFormInSync() {

        // given
        Ingredient ingredient = RecipeFixtures.withId(
            new Ingredient("cebule", IngredientStatus.NEW), 3L);
        given(ingredients.findById(3L)).willReturn(Optional.of(ingredient));

        // when
        Ingredient updated = service.update(3L, "Cebula", "warzywa", null, IngredientStatus.VERIFIED);

        // then
        assertThat(updated.getName()).isEqualTo("Cebula");
        assertThat(updated.getNameNormalized()).isEqualTo("cebula");
        assertThat(updated.getStatus()).isEqualTo(IngredientStatus.VERIFIED);
    }

    @Test
    @DisplayName("get_whenMissing_throwsNotFound")
    void get_whenMissing_throwsNotFound() {

        // given
        given(ingredients.findById(9L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("aliasesOf_whenCalled_returnsAliasesOfThatIngredient")
    void aliasesOf_whenCalled_returnsAliasesOfThatIngredient() {

        // given
        Ingredient ingredient = new Ingredient("cebula", IngredientStatus.VERIFIED);
        IngredientAlias alias = new IngredientAlias(ingredient, "cebulka", "pl");
        given(aliases.findByIngredientIdOrderByAliasAsc(3L)).willReturn(List.of(alias));

        // when
        List<IngredientAlias> found = service.aliasesOf(3L);

        // then
        assertThat(found).singleElement().extracting(IngredientAlias::getAlias).isEqualTo("cebulka");
    }

    @Test
    @DisplayName("addAlias_whenAliasFree_savesItForIngredient")
    void addAlias_whenAliasFree_savesItForIngredient() {

        // given
        Ingredient ingredient = RecipeFixtures.withId(
            new Ingredient("cebula", IngredientStatus.VERIFIED), 3L);
        given(ingredients.findById(3L)).willReturn(Optional.of(ingredient));
        given(aliases.findByAliasNormalized("cebulka")).willReturn(Optional.empty());
        given(aliases.save(any(IngredientAlias.class))).willAnswer(call -> call.getArgument(0));

        // when
        IngredientAlias created = service.addAlias(3L, "Cebulka", "pl");

        // then
        assertThat(created.getAliasNormalized()).isEqualTo("cebulka");
        assertThat(created.getIngredient()).isEqualTo(ingredient);
    }

    @Test
    @DisplayName("addAlias_whenAliasLeadsElsewhere_throwsConflict")
    void addAlias_whenAliasLeadsElsewhere_throwsConflict() {

        // given: alias ma wskazywać jednoznacznie, inaczej dopasowanie przy
        // imporcie byłoby losowaniem
        Ingredient ingredient = RecipeFixtures.withId(
            new Ingredient("cebula", IngredientStatus.VERIFIED), 3L);
        Ingredient other = new Ingredient("por", IngredientStatus.VERIFIED);
        given(ingredients.findById(3L)).willReturn(Optional.of(ingredient));
        given(aliases.findByAliasNormalized("cebulka"))
            .willReturn(Optional.of(new IngredientAlias(other, "cebulka", "pl")));

        // when & then
        assertThatThrownBy(() -> service.addAlias(3L, "cebulka", "pl"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("deleteAlias_whenAliasBelongsToAnotherIngredient_throwsNotFound")
    void deleteAlias_whenAliasBelongsToAnotherIngredient_throwsNotFound() {

        // given
        Ingredient other = RecipeFixtures.withId(new Ingredient("por", IngredientStatus.VERIFIED), 4L);
        IngredientAlias alias = RecipeFixtures.withId(new IngredientAlias(other, "porek", "pl"), 8L);
        given(aliases.findById(8L)).willReturn(Optional.of(alias));

        // when & then
        assertThatThrownBy(() -> service.deleteAlias(3L, 8L)).isInstanceOf(NotFoundException.class);
        verify(aliases, never()).delete(any());
    }

    @Test
    @DisplayName("deleteAlias_whenAliasBelongsToIngredient_removesIt")
    void deleteAlias_whenAliasBelongsToIngredient_removesIt() {

        // given
        Ingredient ingredient = RecipeFixtures.withId(
            new Ingredient("cebula", IngredientStatus.VERIFIED), 3L);
        IngredientAlias alias = RecipeFixtures.withId(
            new IngredientAlias(ingredient, "cebulka", "pl"), 8L);
        given(aliases.findById(8L)).willReturn(Optional.of(alias));

        // when
        service.deleteAlias(3L, 8L);

        // then
        verify(aliases).delete(alias);
    }
}
