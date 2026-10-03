package com.pgoogol.diagnostics.core.store;

import java.util.Locale;
import java.util.Objects;

/**
 * Token zapytania po pierwszej fazie kształtu: słowo, identyfikator w cudzysłowie,
 * znak {@code ?} w miejscu wartości albo pojedynczy znak interpunkcji.
 *
 * @param text        treść tokenu
 * @param spaceBefore czy w oryginale przed tokenem stał biały znak albo komentarz
 */
record SqlToken(String text, boolean spaceBefore) {

    boolean isKeyword(String keyword) {

        return text.equalsIgnoreCase(keyword);
    }

    boolean is(String symbol) {

        return Objects.equals(text, symbol);
    }

    String lowerText() {

        return text.toLowerCase(Locale.ROOT);
    }
}
