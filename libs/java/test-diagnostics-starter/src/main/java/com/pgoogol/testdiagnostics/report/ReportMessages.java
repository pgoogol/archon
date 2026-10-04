package com.pgoogol.testdiagnostics.report;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Teksty raportu w wybranym języku: angielskim albo polskim.
 *
 * <p>Wzorce z argumentami formatuje {@link String#format} z {@link Locale#ROOT}, więc
 * liczby wyglądają tak samo w obu językach; procent we wzorcu zapisuje się jako
 * {@code %%}. Objaśnienia czytane przez {@link #lines(String)} idą bez formatowania.</p>
 *
 * <p>Pakiet tekstów ładuje się bez zapasu na język JVM
 * ({@link ResourceBundle.Control#getNoFallbackControl}). Ze zwykłym zapasem prośba
 * o angielski na polskim systemie dostałaby polskie teksty, bo angielski leży w pliku
 * bazowym, a nie w {@code messages_en}.</p>
 */
public final class ReportMessages {

    static final String BUNDLE = "com.pgoogol.testdiagnostics.report.messages";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "pl");

    private final Locale locale;

    private final ResourceBundle bundle;

    private ReportMessages(Locale locale) {

        this.locale = locale;
        ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
            ResourceBundle.Control.FORMAT_PROPERTIES);
        this.bundle = ResourceBundle.getBundle(BUNDLE, locale, control);
    }

    /**
     * @param requested {@code pl} albo {@code en}; pusty oznacza język JVM, gdy jest
     *                  obsługiwany, a w każdym innym przypadku raport jest po angielsku
     */
    public static ReportMessages forLanguage(String requested) {

        Locale jvmDefault = Locale.getDefault();
        Locale locale = resolveLocale(requested, jvmDefault);
        return new ReportMessages(locale);
    }

    static Locale resolveLocale(String requested, Locale jvmDefault) {

        String trimmed = Objects.toString(requested, "").strip();
        String language = trimmed.toLowerCase(Locale.ROOT);
        if (language.isEmpty()) {

            language = jvmDefault.getLanguage();
        }
        if (SUPPORTED_LANGUAGES.contains(language)) {

            return Locale.of(language);
        }
        return Locale.ENGLISH;
    }

    public Locale locale() {

        return locale;
    }

    /** Tekst spod klucza z podstawionymi argumentami. */
    public String text(String key, Object... args) {

        String pattern = bundle.getString(key);
        return String.format(Locale.ROOT, pattern, args);
    }

    /** Objaśnienie z kilku linii, bez formatowania. */
    public List<String> lines(String key) {

        String text = bundle.getString(key);
        return text.lines().toList();
    }

    /** Nazwa przebiegu do nagłówka: {@code unit} → „unit tests”; nieznany identyfikator zostaje bez zmian. */
    public String runLabel(String id) {

        return labelOrId("label.", id);
    }

    /** Nazwa atrybutu konfiguracji kontekstu, np. {@code profiles} → „profiles”; nieznany zostaje bez zmian. */
    public String attributeLabel(String attribute) {

        return labelOrId("attribute.", attribute);
    }

    private String labelOrId(String prefix, String id) {

        String key = prefix + id;
        if (bundle.containsKey(key)) {

            return bundle.getString(key);
        }
        return id;
    }
}
