package com.pgoogol.kitchen.revision.diff;

/** Skąd wzięła się rewizja — widoczne w historii przepisu. */
public enum RevisionOrigin {

    /** Formularz: założenie przepisu albo ręczna edycja. */
    MANUAL,

    /** Akceptacja szkicu z importu (link, tekst, zdjęcia). */
    IMPORT,

    /** Przepis przysłany przez czat. */
    CHAT,

    /** Przywrócenie starszej wersji — zapisane jako kolejna rewizja, nie cofnięcie. */
    RESTORE
}
