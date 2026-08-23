package com.pgoogol.finance.api;

import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryNode;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CategoryApiMapper {

    CategoryResponse toResponse(CategoryNode node);

    List<CategoryResponse> toResponses(List<CategoryNode> nodes);

    /**
     * Pojedyncza kategoria po zapisie — bez potomków, bo świeżo utworzona ich nie ma,
     * a po edycji klient i tak przeładowuje drzewo.
     */
    @Mapping(target = "children", expression = "java(java.util.List.of())")
    CategoryResponse toResponse(Category category);
}
