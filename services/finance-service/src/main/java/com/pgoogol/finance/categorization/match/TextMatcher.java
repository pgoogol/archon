package com.pgoogol.finance.categorization.match;

import org.apache.commons.lang3.StringUtils;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
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

            return StringUtils.EMPTY;
        }
        String trimmed = value.trim();
        Matcher spaces = SPACES.matcher(trimmed);
        String collapsed = spaces.replaceAll(" ");
        return collapsed.toUpperCase(Locale.ROOT);
    }
}
