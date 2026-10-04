package com.pgoogol.diagnostics.core.store;

import com.pgoogol.diagnostics.core.OperationKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rodzaj operacji PostgreSQL z tokenów zapytania. Działa na tokenach, nie na tekście,
 * więc komentarz na początku ani słowo kluczowe w literale nie mylą wyniku.
 *
 * <ul>
 *     <li>{@code SELECT}, {@code SHOW}, {@code WITH … SELECT} → {@code READ};</li>
 *     <li>{@code INSERT}, {@code UPDATE}, {@code DELETE}, {@code MERGE},
 *     {@code WITH … INSERT/UPDATE/DELETE/MERGE} → {@code WRITE};</li>
 *     <li>reszta → {@code OTHER}.</li>
 * </ul>
 */
final class PostgreSqlStatementClassifier {

    private static final Set<String> READS = Set.of("select", "show");

    private static final Set<String> WRITES = Set.of("insert", "update", "delete", "merge");

    private static final Set<String> MAIN_STATEMENTS = Set.of("select", "insert", "update", "delete", "merge");

    OperationKind classify(List<SqlToken> tokens) {

        String keyword = firstWord(tokens, 0);
        if (Objects.equals(keyword, "with")) {

            return classifyWith(tokens);
        }
        return kindOf(keyword);
    }

    /**
     * Rodzaj {@code WITH} wyznacza główne polecenie po ostatnim CTE, ale CTE z
     * {@code DELETE … RETURNING} zmienia dane także pod głównym {@code SELECT}.
     * Ciało każdego CTE stoi w nawiasie na zewnętrznym poziomie, więc wystarczy
     * sprawdzić pierwsze słowo w każdym takim nawiasie.
     */
    private OperationKind classifyWith(List<SqlToken> tokens) {

        List<Integer> topLevel = topLevelIndexes(tokens);
        boolean modifyingCte = topLevel.stream()
            .filter(index -> tokens.get(index).is("("))
            .map(index -> firstWord(tokens, index))
            .anyMatch(WRITES::contains);
        if (modifyingCte) {

            return OperationKind.WRITE;
        }
        String main = topLevel.stream()
            .skip(1)
            .map(index -> tokens.get(index).lowerText())
            .filter(MAIN_STATEMENTS::contains)
            .findFirst()
            .orElse("");
        return kindOf(main);
    }

    private OperationKind kindOf(String keyword) {

        if (READS.contains(keyword)) {

            return OperationKind.READ;
        }
        if (WRITES.contains(keyword)) {

            return OperationKind.WRITE;
        }
        return OperationKind.OTHER;
    }

    /**
     * Pierwszy token od {@code start}, który nie jest nawiasem otwierającym, małymi literami;
     * pusty tekst, gdy takiego nie ma. Pomija nawiasy, bo zapytanie może zaczynać się od
     * {@code (SELECT …) UNION …}.
     */
    private String firstWord(List<SqlToken> tokens, int start) {

        return tokens.stream()
            .skip(start)
            .filter(token -> !token.is("("))
            .findFirst()
            .map(SqlToken::lowerText)
            .orElse("");
    }

    /** Pozycje tokenów leżących poza wszystkimi nawiasami; nawias otwierający też się liczy. */
    private List<Integer> topLevelIndexes(List<SqlToken> tokens) {

        List<Integer> indexes = new ArrayList<>();
        int depth = 0;
        // pętla indeksowa: wynikiem są pozycje, a głębokość to stan niesiony między tokenami
        for (int index = 0; index < tokens.size(); index++) {

            SqlToken token = tokens.get(index);
            if (depth == 0) {

                indexes.add(index);
            }
            depth += depthChange(token);
        }
        return indexes;
    }

    private int depthChange(SqlToken token) {

        if (token.is("(")) {

            return 1;
        }
        if (token.is(")")) {

            return -1;
        }
        return 0;
    }
}
