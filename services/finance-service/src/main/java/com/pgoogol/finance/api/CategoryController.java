package com.pgoogol.finance.api;

import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.category.domain.CategoryNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/categories")
@Tag(name = "categories", description = "Drzewo kategorii")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryApiMapper mapper;

    @GetMapping
    @Operation(summary = "Drzewo kategorii",
        description = "Zwraca korzenie; podkategorie siedzą w polu children.")
    public List<CategoryResponse> listCategories(
            @RequestParam(required = false) CategoryDirection direction,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<CategoryNode> tree = categoryService.tree(direction, includeArchived);
        return mapper.toResponses(tree);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa kategoria lub podkategoria")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {

        Category category = categoryService.create(
            request.parentId(), request.name(), request.direction());
        return mapper.toResponse(category);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmiana kategorii")
    public CategoryResponse updateCategory(@PathVariable long id,
                                           @Valid @RequestBody CategoryRequest request) {

        Category category = categoryService.update(
            id, request.parentId(), request.name(), request.direction());
        return mapper.toResponse(category);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archiwizacja kategorii",
        description = """
            Kategoria zostaje w bazie — inaczej historyczne transakcje \
            straciłyby przypisanie.""")
    public void archiveCategory(@PathVariable long id) {

        categoryService.archive(id);
    }
}
