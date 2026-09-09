package com.pgoogol.kitchen.recipe.domain;

/** Skąd przyszła treść przepisu. */
public enum SourceKind {

    /** Adres strony z przepisem. */
    URL,

    /** Wklejony tekst. */
    TEXT,

    /** Zdjęcia albo PDF. */
    FILES,

    /** Przepis przysłany przez czat. */
    CHAT,

    /** Wpisany ręcznie w formularzu. */
    MANUAL
}
