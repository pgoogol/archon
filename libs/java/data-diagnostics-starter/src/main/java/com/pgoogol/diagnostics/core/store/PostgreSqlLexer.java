package com.pgoogol.diagnostics.core.store;

import java.util.List;
import java.util.Optional;

/**
 * Pierwsza faza kształtu zapytania PostgreSQL: dzieli tekst na tokeny, po drodze usuwa
 * komentarze, a każdy literał i parametr zamienia na {@code ?}.
 *
 * <p>Zamieniane na {@code ?}: literały tekstowe ({@code '…'}, {@code E'…'}, {@code B'…'},
 * {@code X'…'}, {@code N'…'}, {@code $$…$$}, {@code $tag$…$tag$}), liczbowe
 * ({@code 42}, {@code 3.14}, {@code .5}, {@code 1e-3}) i parametry pozycyjne
 * ({@code $1}). Bez zmian zostają słowa (także z cyframi, jak alias Hibernate
 * {@code o1_0}), identyfikatory w cudzysłowach i znaki interpunkcji, więc rzutowanie
 * {@code ::date} przechodzi do kształtu.</p>
 *
 * <p>Skaner chodzi pętlami po indeksie, bo każda konstrukcja zjada inną liczbę znaków.
 * Nie rzuca wyjątków: niedomknięty literał albo komentarz kończy się z końcem tekstu.</p>
 */
final class PostgreSqlLexer {

    private static final String PLACEHOLDER = "?";

    /** Litery, które przed apostrofem zaczynają literał: escape, bitowy, szesnastkowy, narodowy. */
    private static final String STRING_PREFIXES = "eEbBxXnN";

    List<SqlToken> tokenize(String statement) {

        SqlCursor cursor = new SqlCursor(statement);
        while (cursor.hasMore()) {

            step(cursor);
        }
        return cursor.tokens();
    }

    private void step(SqlCursor cursor) {

        boolean separator = skipWhitespace(cursor) || skipLineComment(cursor) || skipBlockComment(cursor);
        if (separator) {

            cursor.markSeparator();
            return;
        }
        boolean value = skipStringLiteral(cursor)
            || skipDollarQuoted(cursor)
            || skipPositionalParameter(cursor)
            || skipNumber(cursor);
        if (value) {

            cursor.emit(PLACEHOLDER);
            return;
        }
        copyToken(cursor);
    }

    private boolean skipWhitespace(SqlCursor cursor) {

        if (!Character.isWhitespace(cursor.current())) {

            return false;
        }
        cursor.advanceWhile(Character::isWhitespace);
        return true;
    }

    private boolean skipLineComment(SqlCursor cursor) {

        if (!cursor.startsWith("--")) {

            return false;
        }
        cursor.advanceWhile(character -> character != '\n');
        return true;
    }

    /** Komentarze blokowe w PostgreSQL się zagnieżdżają, więc liczymy głębokość. */
    private boolean skipBlockComment(SqlCursor cursor) {

        if (!cursor.startsWith("/*")) {

            return false;
        }
        int depth = 0;
        do {

            depth += stepInsideComment(cursor);
        } while (depth > 0 && cursor.hasMore());
        return true;
    }

    /** Przesuwa kursor o krok wewnątrz komentarza i zwraca zmianę głębokości. */
    private int stepInsideComment(SqlCursor cursor) {

        if (cursor.startsWith("/*")) {

            cursor.advance(2);
            return 1;
        }
        if (cursor.startsWith("*/")) {

            cursor.advance(2);
            return -1;
        }
        cursor.advance(1);
        return 0;
    }

    private boolean skipStringLiteral(SqlCursor cursor) {

        if (cursor.current() == '\'') {

            cursor.advance(1);
            skipQuotedBody(cursor, '\'', false);
            return true;
        }
        boolean prefixed = STRING_PREFIXES.indexOf(cursor.current()) >= 0 && cursor.peek(1) == '\'';
        if (!prefixed) {

            return false;
        }
        boolean backslashEscapes = Character.toLowerCase(cursor.current()) == 'e';
        cursor.advance(2);
        skipQuotedBody(cursor, '\'', backslashEscapes);
        return true;
    }

    /** Przechodzi za cudzysłów zamykający; kursor stoi tuż za otwierającym. */
    private void skipQuotedBody(SqlCursor cursor, char quote, boolean backslashEscapes) {

        boolean closed = false;
        while (!closed && cursor.hasMore()) {

            closed = stepInsideQuotes(cursor, quote, backslashEscapes);
        }
    }

    /** Podwojony cudzysłów to znak w treści, w {@code E'…'} także znak po {@code \}. */
    private boolean stepInsideQuotes(SqlCursor cursor, char quote, boolean backslashEscapes) {

        char current = cursor.current();
        boolean escaped = (backslashEscapes && current == '\\')
            || (current == quote && cursor.peek(1) == quote);
        if (escaped) {

            cursor.advance(2);
            return false;
        }
        cursor.advance(1);
        return current == quote;
    }

    private boolean skipDollarQuoted(SqlCursor cursor) {

        Optional<String> delimiter = dollarDelimiter(cursor);
        if (delimiter.isEmpty()) {

            return false;
        }
        String tag = delimiter.get();
        cursor.advance(tag.length());
        cursor.advancePast(tag);
        return true;
    }

    /** Ogranicznik {@code $$} albo {@code $etykieta$} zaczynający się na pozycji kursora. */
    private Optional<String> dollarDelimiter(SqlCursor cursor) {

        if (cursor.current() != '$') {

            return Optional.empty();
        }
        int length = 1;
        while (isTagCharacter(cursor.peek(length), length == 1)) {

            length++;
        }
        if (cursor.peek(length) != '$') {

            return Optional.empty();
        }
        String delimiter = cursor.lookahead(length + 1);
        return Optional.of(delimiter);
    }

    private boolean isTagCharacter(char character, boolean first) {

        if (first) {

            return Character.isLetter(character) || character == '_';
        }
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private boolean skipPositionalParameter(SqlCursor cursor) {

        if (cursor.current() != '$' || !Character.isDigit(cursor.peek(1))) {

            return false;
        }
        cursor.advance(1);
        cursor.advanceWhile(Character::isDigit);
        return true;
    }

    private boolean skipNumber(SqlCursor cursor) {

        char current = cursor.current();
        boolean startsNumber = Character.isDigit(current)
            || (current == '.' && Character.isDigit(cursor.peek(1)));
        if (!startsNumber) {

            return false;
        }
        boolean inside = true;
        while (inside && cursor.hasMore()) {

            inside = stepInsideNumber(cursor);
        }
        return true;
    }

    /**
     * Cyfry, kropka, podkreślenie ({@code 1_000}) i litery ({@code 1e10}, {@code 0x1F});
     * znak po wykładniku należy do liczby tylko wtedy, gdy stoi za nim cyfra.
     */
    private boolean stepInsideNumber(SqlCursor cursor) {

        char current = cursor.current();
        if (!Character.isLetterOrDigit(current) && current != '.' && current != '_') {

            return false;
        }
        boolean signedExponent = Character.toLowerCase(current) == 'e'
            && (cursor.peek(1) == '+' || cursor.peek(1) == '-')
            && Character.isDigit(cursor.peek(2));
        int width = 1;
        if (signedExponent) {

            width = 2;
        }
        cursor.advance(width);
        return true;
    }

    private void copyToken(SqlCursor cursor) {

        boolean copied = copyQuotedIdentifier(cursor) || copyWord(cursor);
        if (copied) {

            return;
        }
        int start = cursor.position();
        cursor.advance(1);
        cursor.emitFrom(start);
    }

    private boolean copyQuotedIdentifier(SqlCursor cursor) {

        if (cursor.current() != '"') {

            return false;
        }
        int start = cursor.position();
        cursor.advance(1);
        skipQuotedBody(cursor, '"', false);
        cursor.emitFrom(start);
        return true;
    }

    private boolean copyWord(SqlCursor cursor) {

        char current = cursor.current();
        if (!Character.isLetter(current) && current != '_') {

            return false;
        }
        int start = cursor.position();
        cursor.advanceWhile(character -> Character.isLetterOrDigit(character) || character == '_' || character == '$');
        cursor.emitFrom(start);
        return true;
    }
}
