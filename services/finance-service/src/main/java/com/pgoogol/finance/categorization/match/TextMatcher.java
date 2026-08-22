package com.pgoogol.finance.categorization.match;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Dopasowanie tekstu wyciągu do wzorca — zawieranie, nie wyrażenie regularne.
 *
 * <p>Normalizacja jest ta sama co przy kluczu deduplikacji: obcięcie brzegów,
 * zwinięcie białych znaków i wielkie litery. Bank potrafi wydrukować ten sam
 * opis raz z podwójną spacją, raz bez.</p>
 */
public class TextMatcher {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    public boolean contains(String text, String pattern) {

        if (Objects.isNull(text) || Objects.isNull(pattern)) {

            return false;
        }
        String normalizedPattern = normalize(pattern);
        if (normalizedPattern.isEmpty()) {

            return false;
        }
        String normalizedText = normalize(text);
        return normalizedText.contains(normalizedPattern);
    }

    public String normalize(String value) {

        if (Objects.isNull(value)) {

            return "";
        }
        String trimmed = value.trim();
        String collapsed = SPACES.matcher(trimmed).replaceAll(" ");
        return collapsed.toUpperCase(Locale.ROOT);
    }
}
