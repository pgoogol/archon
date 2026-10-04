package com.pgoogol.testdiagnostics.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMessagesTest {

    private static final String ENGLISH = "/com/pgoogol/testdiagnostics/report/messages.properties";

    private static final String POLISH = "/com/pgoogol/testdiagnostics/report/messages_pl.properties";

    /** Specyfikator formatu bez {@code %%}: litera konwersji na końcu, np. {@code %-12s}, {@code %3d}. */
    private static final Pattern CONVERSION = Pattern.compile("%[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z])");

    @Test
    @DisplayName("oba pliki tekstów mają ten sam zbiór kluczy")
    void bundles_haveSameKeys() throws IOException {

        // given
        Properties english = load(ENGLISH);
        Properties polish = load(POLISH);

        // when & then
        assertThat(polish.stringPropertyNames()).isEqualTo(english.stringPropertyNames());
    }

    @Test
    @DisplayName("każdy wzorzec ma w obu językach te same argumenty w tej samej kolejności")
    void bundles_haveSameConversionsPerKey() throws IOException {

        // given
        Properties english = load(ENGLISH);
        Properties polish = load(POLISH);

        // when
        Map<String, List<String>> englishConversions = conversions(english);
        Map<String, List<String>> polishConversions = conversions(polish);

        // then
        assertThat(polishConversions).isEqualTo(englishConversions);
    }

    @ParameterizedTest(name = "prośba „{0}” na JVM „{1}” → {2}")
    @CsvSource({
        "pl,   en, pl",
        "EN,   pl, en",
        "' en ', pl, en",
        "de,   pl, en",
        "'',   pl, pl",
        "'',   en, en",
        "'',   fr, en"
    })
    @DisplayName("język raportu: jawny, inaczej język JVM, gdy obsługiwany, a w końcu angielski")
    void resolveLocale_picksRequestedThenJvmThenEnglish(String requested, String jvmLanguage, String expected) {

        // given
        Locale jvmDefault = Locale.of(jvmLanguage);

        // when
        Locale locale = ReportMessages.resolveLocale(requested, jvmDefault);

        // then
        assertThat(locale.getLanguage()).isEqualTo(expected);
    }

    @Test
    @DisplayName("prośba o angielski na polskiej JVM daje angielskie teksty, nie polskie z zapasu")
    void forLanguage_whenEnglishOnPolishJvm_staysEnglish() {

        // given
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.of("pl"));
        String title;

        // when
        try {

            ReportMessages messages = ReportMessages.forLanguage("en");
            title = messages.text("section.result");
        } finally {

            Locale.setDefault(previous);
        }

        // then
        assertThat(title).isEqualTo("RESULT");
    }

    @Test
    @DisplayName("znana etykieta przebiegu dostaje tłumaczenie, nieznana zostaje bez zmian")
    void runLabel_translatesKnownAndKeepsUnknown() {

        // given
        ReportMessages polish = ReportMessages.forLanguage("pl");

        // when & then
        assertThat(List.of(polish.runLabel("unit"), polish.runLabel("nightly")))
            .containsExactly("testy jednostkowe", "nightly");
    }

    @Test
    @DisplayName("objaśnienie dzieli się na linie bez formatowania, więc procent zostaje pojedynczy")
    void lines_splitNoteWithoutFormatting() {

        // given
        ReportMessages english = ReportMessages.forLanguage("en");

        // when
        List<String> lines = english.lines("memory.note");

        // then
        assertThat(lines).hasSize(5).anyMatch(line -> line.contains("Below 70% of the limit"));
    }

    /** Objaśnienia {@code *.note} pomija: to tekst bez formatowania, w którym procent stoi samotnie. */
    private static Map<String, List<String>> conversions(Properties properties) {

        return properties.stringPropertyNames().stream()
            .filter(key -> !key.endsWith(".note"))
            .collect(Collectors.toMap(key -> key, key -> conversionsOf(properties.getProperty(key))));
    }

    private static List<String> conversionsOf(String pattern) {

        String withoutPercentSigns = pattern.replace("%%", "");
        Matcher matcher = CONVERSION.matcher(withoutPercentSigns);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static Properties load(String resource) throws IOException {

        try (InputStream input = ReportMessagesTest.class.getResourceAsStream(resource);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {

            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }
}
