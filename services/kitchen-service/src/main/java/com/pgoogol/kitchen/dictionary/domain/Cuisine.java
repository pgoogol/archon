package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Kuchnia świata: polska, włoska, tajska. */
@Entity
@Table(name = "cuisine")
public class Cuisine extends DictionaryTerm {

    protected Cuisine() {

    }

    public Cuisine(String name) {

        super(name);
    }
}
