package com.pgoogol.finance.api;

import com.pgoogol.finance.category.domain.CategoryDirection;

import java.util.List;

/** Węzeł drzewa kategorii; {@code children} jest puste dla liścia, nigdy null. */
public record CategoryResponse(
    long id,
    Long parentId,
    String name,
    CategoryDirection direction,
    boolean archived,
    List<CategoryResponse> children) {

}
