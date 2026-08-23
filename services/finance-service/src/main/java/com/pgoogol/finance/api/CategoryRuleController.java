package com.pgoogol.finance.api;

import com.pgoogol.finance.categorization.application.CategorizationService;
import com.pgoogol.finance.categorization.domain.CategoryRule;
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
import java.util.Objects;

@RestController
@RequestMapping("/finance/api/v1/category-rules")
@Tag(name = "categorization", description = "Reguły podpowiadające kategorię przy imporcie")
@RequiredArgsConstructor
public class CategoryRuleController {

    private static final int DEFAULT_PRIORITY = 100;

    private final CategorizationService categorizationService;
    private final CategoryRuleApiMapper mapper;

    @GetMapping
    @Operation(summary = "Reguły w kolejności rozstrzygania")
    public List<CategoryRuleResponse> listCategoryRules(
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        List<CategoryRule> rules = categorizationService.list(activeOnly);
        return mapper.toResponses(rules);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa reguła kategoryzacji",
        description = """
            Dopasowanie jest zawieraniem tekstu, nie wyrażeniem regularnym. \
            Reguła wypełnia wyłącznie pole sugestii — kategorię i tak potwierdza \
            człowiek w podglądzie wyciągu.""")
    public CategoryRuleResponse createCategoryRule(
            @Valid @RequestBody CategoryRuleRequest request) {

        int priority = priorityOf(request);
        CategoryRule created = categorizationService.create(request.pattern(),
            request.matchField(), request.categoryId(), priority);
        return mapper.toResponse(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmiana reguły")
    public CategoryRuleResponse updateCategoryRule(@PathVariable long id,
                                                   @Valid @RequestBody CategoryRuleRequest request) {

        int priority = priorityOf(request);
        boolean active = Objects.requireNonNullElse(request.active(), true);
        CategoryRule updated = categorizationService.update(id, request.pattern(),
            request.matchField(), request.categoryId(), priority, active);
        return mapper.toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usunięcie reguły",
        description = """
            Reguła nie zostawia po sobie śladu w danych — podpowiadała kategorię, \
            a zapisane kategorie są na transakcjach i zostają nietknięte.""")
    public void deleteCategoryRule(@PathVariable long id) {

        categorizationService.delete(id);
    }

    private int priorityOf(CategoryRuleRequest request) {

        return Objects.requireNonNullElse(request.priority(), DEFAULT_PRIORITY);
    }
}
