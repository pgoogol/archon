package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Kategoria dania: zupa, deser, danie główne. */
@Entity
@Table(name = "recipe_category")
public class RecipeCategory extends DictionaryTerm {

    protected RecipeCategory() {

    }

    public RecipeCategory(String name) {

        super(name);
    }
}
