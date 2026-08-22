package com.pgoogol.finance.imports.statement;

/**
 * Port wejściowy formatu wyciągu. Jedna implementacja na bank albo na format.
 *
 * <p>Dołożenie banku to nowy bean tego interfejsu i nic więcej — reszta modułu
 * nie wie, ile ich jest ani który zadziałał.</p>
 */
public interface StatementParser {

    /**
     * Czy ten parser rozpoznaje format pliku. Sprawdzenie ma być tanie i pewne:
     * decyduje nagłówek, nie zgadywanie po rozszerzeniu.
     */
    boolean supports(SourceFile file);

    /**
     * @param minorUnit liczba miejsc po przecinku waluty konta — parser nie ma
     *                  prawa założyć, że są dwa
     */
    ParsedStatement parse(SourceFile file, int minorUnit);
}
