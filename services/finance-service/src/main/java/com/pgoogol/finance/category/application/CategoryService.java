package com.pgoogol.finance.category.application;

import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.category.domain.CategoryNode;
import com.pgoogol.finance.category.infrastructure.CategoryRepository;
import com.pgoogol.finance.common.ConflictException;
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
        flat.forEach(category -> {
            // lista potomków jest wspólną referencją, więc dziecko wczytane przed
            // rodzicem i tak trafi do jego gałęzi — bez drugiego przebiegu
            CategoryNode node = node(category, childrenByParent);
            Long parentId = category.getParentId();
            if (Objects.isNull(parentId) || !loaded.contains(parentId)) {
                // rodzic odfiltrowany (np. zarchiwizowany) nie może pochłonąć gałęzi
                roots.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
            }
        });
        return List.copyOf(roots);
    }

    public Category get(long id) {

        return categoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND",
                "Kategoria %d nie istnieje".formatted(id)));
    }

    @Transactional
    public Category create(@Nullable Long parentId, String name, CategoryDirection direction) {

        Category parent = Objects.isNull(parentId) ? null : get(parentId);
        requireDirectionMatchesParent(parent, direction);
        requireUniqueName(parentId, name, null);
        return categoryRepository.save(new Category(parent, name, direction));
    }

    @Transactional
    public Category update(long id, @Nullable Long parentId, String name,
                           CategoryDirection direction) {

        Category category = get(id);
        Category parent = Objects.isNull(parentId) ? null : get(parentId);
        requireNoCycle(category, parent);
        requireDirectionMatchesParent(parent, direction);
        requireUniqueName(parentId, name, id);
        category.moveTo(parent, name, direction);
        return category;
    }

    @Transactional
    public void archive(long id) {
        get(id).archive();
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
                "Kategoria o nazwie '%s' już istnieje w tym miejscu drzewa".formatted(name));
        }
    }

    private void requireDirectionMatchesParent(@Nullable Category parent,
                                               CategoryDirection direction) {

        if (Objects.nonNull(parent) && !Objects.equals(parent.getDirection(), direction)) {
            throw new ValidationException("CATEGORY_DIRECTION_MISMATCH",
                "Podkategoria musi mieć ten sam kierunek co rodzic: %s".formatted(
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
                    "Kategoria nie może być swoim własnym przodkiem");
            }
            ancestor = ancestor.getParent();
        }
    }
}
