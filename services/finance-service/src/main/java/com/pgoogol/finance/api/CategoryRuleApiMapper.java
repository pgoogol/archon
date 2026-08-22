package com.pgoogol.finance.api;

import com.pgoogol.finance.categorization.domain.CategoryRule;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CategoryRuleApiMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    CategoryRuleResponse toResponse(CategoryRule rule);

    List<CategoryRuleResponse> toResponses(List<CategoryRule> rules);
}
