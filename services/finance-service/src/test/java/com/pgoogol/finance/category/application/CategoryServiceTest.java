package com.pgoogol.finance.category.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.category.domain.CategoryNode;
import com.pgoogol.finance.category.infrastructure.CategoryRepository;
import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("drzewo składa się jednym przebiegiem, także gdy dziecko wczytało się przed rodzicem")
    void tree_whenChildLoadedBeforeParent_stillNestsUnderParent() {

        // given: kolejność po nazwie stawia "Auto" przed "Transport"
        Category parent = FinanceFixtures.category(1L, "Transport", CategoryDirection.EXPENSE);
        Category child = FinanceFixtures.childCategory(2L, parent, "Auto");
        when(categoryRepository.findTree(any(), anyBoolean())).thenReturn(List.of(child, parent));

        // when
        List<CategoryNode> tree = categoryService.tree(null, false);

        // then
        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().name()).isEqualTo("Transport");
        assertThat(tree.getFirst().children()).extracting(CategoryNode::name).containsExactly("Auto");
    }

    @Test
    @DisplayName("podkategoria odciętego rodzica pokazuje się jako korzeń, zamiast zniknąć")
    void tree_whenParentFilteredOut_promotesChildToRoot() {

        // given
        Category parent = FinanceFixtures.category(1L, "Transport", CategoryDirection.EXPENSE);
        Category child = FinanceFixtures.childCategory(2L, parent, "Auto");
        when(categoryRepository.findTree(any(), anyBoolean())).thenReturn(List.of(child));

        // when
        List<CategoryNode> tree = categoryService.tree(null, false);

        // then
        assertThat(tree).extracting(CategoryNode::name).containsExactly("Auto");
    }

    @Test
    @DisplayName("dwie kategorie o tej samej nazwie u tego samego rodzica to konflikt")
    void create_whenSiblingNameTaken_fails() {

        // given
        when(categoryRepository.existsSibling(null, "Jedzenie", null)).thenReturn(true);

        // when & then
        assertThatThrownBy(() ->
            categoryService.create(null, "Jedzenie", CategoryDirection.EXPENSE))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("podkategoria o innym kierunku niż rodzic jest odrzucana")
    void create_whenDirectionDiffersFromParent_fails() {

        // given
        Category parent = FinanceFixtures.category(1L, "Transport", CategoryDirection.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));

        // when & then
        assertThatThrownBy(() ->
            categoryService.create(1L, "Zwroty", CategoryDirection.INCOME))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("przepięcie kategorii pod własnego potomka zapętliłoby drzewo i jest odrzucane")
    void update_whenNewParentIsOwnDescendant_fails() {

        // given
        Category parent = FinanceFixtures.category(1L, "Transport", CategoryDirection.EXPENSE);
        Category child = FinanceFixtures.childCategory(2L, parent, "Auto");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(child));

        // when & then
        assertThatThrownBy(() ->
            categoryService.update(1L, 2L, "Transport", CategoryDirection.EXPENSE))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("własnym przodkiem");
    }

    @Test
    @DisplayName("usunięcie kategorii jest archiwizacją — transakcje nie tracą przypisania")
    void archive_whenCategoryExists_marksArchived() {

        // given
        Category category = FinanceFixtures.category(1L, "Transport", CategoryDirection.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        // when
        categoryService.archive(1L);

        // then
        assertThat(category.isArchived()).isTrue();
    }
}
