package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Dieta, do której przepis pasuje: wegetariańska, bez laktozy. */
@Entity
@Table(name = "diet")
public class Diet extends DictionaryTerm {

    protected Diet() {

    }

    public Diet(String name) {

        super(name);
    }
}
