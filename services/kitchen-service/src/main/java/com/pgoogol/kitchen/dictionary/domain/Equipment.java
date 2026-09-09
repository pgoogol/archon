package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Sprzęt używany w kroku: piekarnik, blender, termomiks. */
@Entity
@Table(name = "equipment")
public class Equipment extends DictionaryTerm {

    protected Equipment() {

    }

    public Equipment(String name) {

        super(name);
    }
}
