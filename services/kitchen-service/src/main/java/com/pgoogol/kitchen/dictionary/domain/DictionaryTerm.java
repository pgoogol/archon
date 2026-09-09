package com.pgoogol.kitchen.dictionary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.Objects;

/**
 * Wspólny kształt słowników prostych: kuchnia, kategoria, dieta, tag, sprzęt.
 *
 * <p>Każdy z nich ma własną tabelę, mimo identycznej budowy — dzięki temu klucz
 * obcy przepisu do kuchni nie może wskazać diety. Powtórzone są same tabele,
 * nie kod: mapowanie siedzi tutaj, a podklasy noszą wyłącznie nazwę tabeli.</p>
 */
@MappedSuperclass
public abstract class DictionaryTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 100)
    private String nameNormalized;

    protected DictionaryTerm() {

    }

    protected DictionaryTerm(String name) {

        rename(name);
    }

    public final void rename(String name) {

        this.name = Objects.requireNonNull(name, "name").trim();
        this.nameNormalized = NameNormalizer.normalize(this.name);
    }

    public Long getId() {

        return id;
    }

    public String getName() {

        return name;
    }

    public String getNameNormalized() {

        return nameNormalized;
    }
}
