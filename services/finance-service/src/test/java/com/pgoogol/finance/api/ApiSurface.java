package com.pgoogol.finance.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Powierzchnia API sprowadzona do czterech zbiorów, które da się porównać po obu
 * stronach kontraktu: operacje, parametry, pola schematów i wartości enumów.
 *
 * <p>Celowo nie ma tu typów, opisów ani kolejności — springdoc renderuje je
 * inaczej, niż pisze się je ręcznie, więc porównanie bajt w bajt pękałoby na
 * zmianach kosmetycznych zamiast na realnym rozjeździe.</p>
 *
 * @param operations   zbiór {@code METODA ścieżka}
 * @param parameters   nazwy parametrów per operacja
 * @param schemaFields nazwy pól per schemat obiektowy
 * @param enumValues   zbiory wartości enumów, bez nazw — patrz {@link ContractSurface}
 */
record ApiSurface(
    Set<String> operations,
    Map<String, Set<String>> parameters,
    Map<String, Set<String>> schemaFields,
    Set<List<String>> enumValues) {

    /** Metody HTTP, które w OpenAPI są operacją, a nie polem obok niej. */
    static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    static String operation(String method, String path) {

        String upper = method.toUpperCase(Locale.ROOT);
        return upper + " " + path;
    }
}
