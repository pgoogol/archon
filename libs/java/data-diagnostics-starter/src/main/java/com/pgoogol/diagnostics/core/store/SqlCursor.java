package com.pgoogol.diagnostics.core.store;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Kursor po tekście jednego zapytania: pozycja, zebrane tokeny i informacja, czy przed
 * następnym tokenem stał separator. Tworzony na każde zapytanie, dzięki czemu lekser
 * zostaje bezstanowy i bezpieczny dla wielu wątków.
 */
final class SqlCursor {

    /** Wartość {@link #peek(int)} poza końcem tekstu — znak, którego nie ma w SQL. */
    static final char END = '\0';

    private final String text;

    private final List<SqlToken> tokens = new ArrayList<>();

    private int position;

    private boolean separatorPending;

    SqlCursor(String text) {

        this.text = text;
    }

    boolean hasMore() {

        return position < text.length();
    }

    char current() {

        return text.charAt(position);
    }

    /** Znak o {@code offset} dalej niż bieżący albo {@link #END} poza końcem tekstu. */
    char peek(int offset) {

        int index = position + offset;
        if (index >= text.length()) {

            return END;
        }
        return text.charAt(index);
    }

    boolean startsWith(String prefix) {

        return text.startsWith(prefix, position);
    }

    int position() {

        return position;
    }

    /** Tekst od bieżącej pozycji o długości {@code length}, ucięty na końcu zapytania. */
    String lookahead(int length) {

        int end = Math.min(position + length, text.length());
        return text.substring(position, end);
    }

    void advance(int count) {

        position = Math.min(position + count, text.length());
    }

    void advanceWhile(IntPredicate predicate) {

        // pętla zamiast strumienia: kończy się na pierwszym znaku spoza zbioru
        while (hasMore() && predicate.test(current())) {

            position++;
        }
    }

    /** Przesuwa kursor za najbliższe wystąpienie {@code delimiter}, a gdy go nie ma — na koniec. */
    void advancePast(String delimiter) {

        int found = text.indexOf(delimiter, position);
        if (found < 0) {

            position = text.length();
            return;
        }
        position = found + delimiter.length();
    }

    void markSeparator() {

        separatorPending = true;
    }

    void emit(String token) {

        boolean spaceBefore = separatorPending && !tokens.isEmpty();
        tokens.add(new SqlToken(token, spaceBefore));
        separatorPending = false;
    }

    /** Emituje jako token tekst od {@code start} do bieżącej pozycji. */
    void emitFrom(int start) {

        String token = text.substring(start, position);
        emit(token);
    }

    List<SqlToken> tokens() {

        return List.copyOf(tokens);
    }
}
