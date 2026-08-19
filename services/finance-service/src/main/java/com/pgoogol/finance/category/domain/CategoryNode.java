package com.pgoogol.finance.category.domain;

import org.springframework.lang.Nullable;

import java.util.List;

/** Węzeł drzewa kategorii — kształt, w jakim kategorie wychodzą na zewnątrz. */
public record CategoryNode(
    long id,
    @Nullable Long parentId,
    String name,
    CategoryDirection direction,
    boolean archived,
    List<CategoryNode> children) {

}
