package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.recipe.RecipeFixtures;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeStatus;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipes;

    @InjectMocks
    private RecipeService service;

    @Test
    @DisplayName("get_whenRecipeExists_returnsItWithContent")
    void get_whenRecipeExists_returnsItWithContent() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        given(recipes.findWithContentById(1L)).willReturn(Optional.of(recipe));

        // when
        Recipe found = service.get(1L);

        // then
        assertThat(found.getTitle()).isEqualTo("Sernik");
    }

    @Test
    @DisplayName("get_whenRecipeMissing_throwsNotFoundWithCode")
    void get_whenRecipeMissing_throwsNotFoundWithCode() {

        // given
        given(recipes.findWithContentById(7L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.get(7L))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("7")
            .extracting(exception -> ((NotFoundException) exception).getErrorCode())
            .isEqualTo(ErrorCodes.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("list_whenCalled_asksOnlyForActiveRecipes")
    void list_whenCalled_asksOnlyForActiveRecipes() {

        // given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Recipe> page = new PageImpl<>(List.of(RecipeFixtures.recipe(1L, "Sernik")));
        given(recipes.findByStatusOrderByCreatedAtDesc(RecipeStatus.ACTIVE, pageable))
            .willReturn(page);

        // when
        Page<Recipe> result = service.list(pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("archive_whenRecipeExists_marksItArchivedInsteadOfDeleting")
    void archive_whenRecipeExists_marksItArchivedInsteadOfDeleting() {

        // given
        Recipe recipe = RecipeFixtures.recipe(1L, "Sernik");
        given(recipes.findWithContentById(1L)).willReturn(Optional.of(recipe));

        // when
        service.archive(1L);

        // then: dziennik zmian i zlecenia importu trzymają odniesienia do wiersza
        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.ARCHIVED);
    }
}
