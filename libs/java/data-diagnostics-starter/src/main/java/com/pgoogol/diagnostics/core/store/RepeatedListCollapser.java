package com.pgoogol.diagnostics.core.store;

import java.util.ArrayList;
import java.util.List;

/**
 * Druga faza kształtu: zwija listy, których długość zależy od danych, a nie od kodu —
 * {@code IN (?, ?, ?)} i wielowierszowe {@code VALUES (…), (…)}. Bez tego ta sama metoda
 * repozytorium dawałaby osobny kształt dla każdej liczby elementów.
 *
 * <p>Lista zwija się do pierwszego elementu tylko wtedy, gdy wszystkie elementy mają ten
 * sam kształt: {@code IN (a, b)} z różnymi kolumnami zostaje bez zmian. Nawiasy kwadratowe
 * liczą się do głębokości tak jak okrągłe, więc przecinek w {@code ARRAY[…]} nie dzieli
 * elementów.</p>
 */
final class RepeatedListCollapser {

    private static final int NOT_FOUND = -1;

    List<SqlToken> collapse(List<SqlToken> tokens) {

        List<SqlToken> result = new ArrayList<>(tokens.size());
        int index = 0;
        // pętla indeksowa: po zwiniętej liście kursor skacze za jej koniec
        while (index < tokens.size()) {

            index = copyNext(tokens, index, result);
        }
        return result;
    }

    /** Kopiuje token spod {@code index} i zwraca pozycję następnego do przetworzenia. */
    private int copyNext(List<SqlToken> tokens, int index, List<SqlToken> result) {

        SqlToken token = tokens.get(index);
        result.add(token);
        int next = index + 1;
        if (token.isKeyword("in")) {

            return collapseInList(tokens, next, result);
        }
        if (token.isKeyword("values")) {

            return collapseRows(tokens, next, result);
        }
        return next;
    }

    /** {@code IN (x, x, x)}: elementy rozdzielone przecinkami wewnątrz jednego nawiasu. */
    private int collapseInList(List<SqlToken> tokens, int open, List<SqlToken> result) {

        if (!isSymbol(tokens, open, "(")) {

            return open;
        }
        int close = matchingClose(tokens, open);
        if (close == NOT_FOUND) {

            return open;
        }
        List<SqlToken> content = tokens.subList(open + 1, close);
        List<List<SqlToken>> items = splitTopLevel(content);
        if (!allSame(items)) {

            return open;
        }
        appendGroup(tokens.get(open), items.getFirst(), tokens.get(close), result);
        return close + 1;
    }

    /** {@code VALUES (…), (…)}: każdy wiersz w osobnym nawiasie, wiersze po przecinku. */
    private int collapseRows(List<SqlToken> tokens, int first, List<SqlToken> result) {

        List<Group> rows = rows(tokens, first);
        List<List<SqlToken>> contents = rows.stream()
            .map(row -> tokens.subList(row.open() + 1, row.close()))
            .toList();
        if (!allSame(contents)) {

            return first;
        }
        Group firstRow = rows.getFirst();
        appendGroup(tokens.get(firstRow.open()), contents.getFirst(), tokens.get(firstRow.close()), result);
        return rows.getLast().close() + 1;
    }

    private List<Group> rows(List<SqlToken> tokens, int first) {

        List<Group> rows = new ArrayList<>();
        int open = first;
        boolean more = isSymbol(tokens, open, "(");
        // pętla zamiast strumienia: kolejny wiersz istnieje tylko wtedy, gdy po poprzednim
        // stoi przecinek i nawias
        while (more) {

            int close = matchingClose(tokens, open);
            if (close == NOT_FOUND) {

                return rows;
            }
            rows.add(new Group(open, close));
            more = isSymbol(tokens, close + 1, ",") && isSymbol(tokens, close + 2, "(");
            open = close + 2;
        }
        return rows;
    }

    /** Dzieli zawartość nawiasu na elementy po przecinkach leżących na zewnętrznym poziomie. */
    private List<List<SqlToken>> splitTopLevel(List<SqlToken> content) {

        List<List<SqlToken>> items = new ArrayList<>();
        List<SqlToken> item = new ArrayList<>();
        int depth = 0;
        // pętla zamiast strumienia: podział zależy od głębokości nawiasów, czyli od stanu
        // niesionego między tokenami
        for (SqlToken token : content) {

            depth += depthChange(token);
            if (depth == 0 && token.is(",")) {

                items.add(item);
                item = new ArrayList<>();
                continue;
            }
            item.add(token);
        }
        items.add(item);
        return items;
    }

    /** Co najmniej dwa elementy i wszystkie o tym samym kształcie, niezależnie od odstępów. */
    private boolean allSame(List<List<SqlToken>> items) {

        if (items.size() < 2) {

            return false;
        }
        List<String> first = texts(items.getFirst());
        return items.stream()
            .map(this::texts)
            .allMatch(first::equals);
    }

    private List<String> texts(List<SqlToken> tokens) {

        return tokens.stream()
            .map(SqlToken::text)
            .toList();
    }

    /** Dopisuje nawias z jednym elementem; listy zagnieżdżone w elemencie też się zwijają. */
    private void appendGroup(SqlToken open, List<SqlToken> content, SqlToken close, List<SqlToken> result) {

        List<SqlToken> collapsedContent = collapse(content);
        result.add(open);
        result.addAll(collapsedContent);
        result.add(close);
    }

    private int matchingClose(List<SqlToken> tokens, int open) {

        int depth = 0;
        // pętla indeksowa: wynikiem jest pozycja, a głębokość to stan niesiony między tokenami
        for (int index = open; index < tokens.size(); index++) {

            depth += depthChange(tokens.get(index));
            if (depth == 0) {

                return index;
            }
        }
        return NOT_FOUND;
    }

    private int depthChange(SqlToken token) {

        if (token.is("(") || token.is("[")) {

            return 1;
        }
        if (token.is(")") || token.is("]")) {

            return -1;
        }
        return 0;
    }

    private boolean isSymbol(List<SqlToken> tokens, int index, String symbol) {

        return index < tokens.size() && tokens.get(index).is(symbol);
    }

    /** Pozycje nawiasu otwierającego i domykającego go. */
    private record Group(int open, int close) {

    }
}
