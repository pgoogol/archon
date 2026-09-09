package com.pgoogol.kitchen.dictionary.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Postać porównawcza nazwy: małe litery, bez ogonków, bez podwójnych spacji.
 * „Cebula Czerwona ", „cebula czerwona" i „CEBULA CZERWONA" mają dać ten sam
 * łańcuch, bo po nim idzie dopasowanie do słownika i do katalogu składników.
 *
 * <p>Bezstanowa i bez frameworka — te same reguły stosuje migracja przy
 * zasiewaniu słowników, więc muszą dać się sprawdzić testem jednostkowym.</p>
 */
public final class NameNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private NameNormalizer() {

    }

    public static String normalize(String value) {

        if (Objects.isNull(value)) {

            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        // ł nie rozkłada się na literę i znak diakrytyczny — trzeba ją podmienić wprost
        String withoutStroke = lower.replace('ł', 'l');
        String decomposed = Normalizer.normalize(withoutStroke, Normalizer.Form.NFD);
        String stripped = DIACRITICS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(stripped).replaceAll(" ");
    }
}
