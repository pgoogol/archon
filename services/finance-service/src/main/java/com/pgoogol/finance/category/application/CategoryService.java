package com.pgoogol.finance.category.application;

import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.category.domain.CategoryNode;
import com.pgoogol.finance.category.infrastructure.CategoryRepository;
import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {

        this.categoryRepository = categoryRepository;
    }

    /** Drzewo kategorii: jedno zapytanie do bazy, struktura składana w pamięci. */
    public List<CategoryNode> tree(@Nullable CategoryDirection direction, boolean includeArchived) {

        List<Category> flat = categoryRepository.findTree(direction, includeArchived);
        Set<Long> loaded = flat.stream().map(Category::getId).collect(Collectors.toSet());
        Map<Long, List<CategoryNode>> childrenByParent = new LinkedHashMap<>();
        List<CategoryNode> roots = new ArrayList<>();
        flat.forEach(category -> attach(category, loaded, childrenByParent, roots));
        return List.copyOf(roots);
    }

    /**
     * Wpina kategorię w drzewo. Lista potomków jest wspólną referencją, więc
     * dziecko wczytane przed rodzicem i tak trafi do jego gałęzi — bez drugiego
     * przebiegu po danych.
     */
    private void attach(Category category, Set<Long> loaded,
                        Map<Long, List<CategoryNode>> childrenByParent, List<CategoryNode> roots) {

        CategoryNode node = node(category, childrenByParent);
        Long parentId = category.getParentId();
        if (Objects.isNull(parentId) || !loaded.contains(parentId)) {
            // rodzic odfiltrowany (np. zarchiwizowany) nie może pochłonąć gałęzi
            roots.add(node);
            return;
        }
        List<CategoryNode> siblings = childrenByParent.computeIfAbsent(parentId,
            key -> new ArrayList<>());
        siblings.add(node);
    }

    public Category get(long id) {

        return categoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND",
                ExceptionMessageConstants.CATEGORY_NOT_FOUND.formatted(id)));
    }

    @Transactional
    public Category create(@Nullable Long parentId, String name, CategoryDirection direction) {

        Category parent = parentOrNull(parentId);
        requireDirectionMatchesParent(parent, direction);
        requireUniqueName(parentId, name, null);
        return categoryRepository.save(new Category(parent, name, direction));
    }

    @Transactional
    public Category update(long id, @Nullable Long parentId, String name,
                           CategoryDirection direction) {

        Category category = get(id);
        Category parent = parentOrNull(parentId);
        requireNoCycle(category, parent);
        requireDirectionMatchesParent(parent, direction);
        requireUniqueName(parentId, name, id);
        category.moveTo(parent, name, direction);
        return category;
    }

    @Transactional
    public void archive(long id) {

        Category category = get(id);
        category.archive();
    }

    @Nullable
    private Category parentOrNull(@Nullable Long parentId) {

        if (Objects.isNull(parentId)) {
            return null;
        }
        return get(parentId);
    }

    private CategoryNode node(Category category, Map<Long, List<CategoryNode>> childrenByParent) {

        return new CategoryNode(
            category.getId(),
            category.getParentId(),
            category.getName(),
            category.getDirection(),
            category.isArchived(),
            childrenByParent.computeIfAbsent(category.getId(), key -> new ArrayList<>()));
    }

    private void requireUniqueName(@Nullable Long parentId, String name, @Nullable Long excludeId) {

        if (categoryRepository.existsSibling(parentId, name, excludeId)) {
            throw new ConflictException("CATEGORY_EXISTS",
                ExceptionMessageConstants.CATEGORY_EXISTS.formatted(name));
        }
    }

    private void requireDirectionMatchesParent(@Nullable Category parent,
                                               CategoryDirection direction) {

        if (Objects.nonNull(parent) && !Objects.equals(parent.getDirection(), direction)) {
            throw new ValidationException("CATEGORY_DIRECTION_MISMATCH",
                ExceptionMessageConstants.CATEGORY_PARENT_DIRECTION_MISMATCH.formatted(
                    parent.getDirection()));
        }
    }

    /**
     * Przepięcie kategorii pod własnego potomka odcięłoby całą gałąź od drzewa
     * i zapętliło każde jego przechodzenie.
     */
    private void requireNoCycle(Category category, @Nullable Category newParent) {

        Category ancestor = newParent;
        while (Objects.nonNull(ancestor)) {
            if (Objects.equals(ancestor.getId(), category.getId())) {
                throw new ValidationException("CATEGORY_CYCLE",
                    ExceptionMessageConstants.CATEGORY_CYCLE);
            }
            ancestor = ancestor.getParent();
        }
    }
}
