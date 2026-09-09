package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Swobodna etykieta nadana przez właściciela — jedyny słownik, który rośnie sam z importów. */
@Entity
@Table(name = "tag")
public class Tag extends DictionaryTerm {

    protected Tag() {

    }

    public Tag(String name) {

        super(name);
    }
}
