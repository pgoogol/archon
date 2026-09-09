package com.pgoogol.llm.prompt;

import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmNotConfiguredException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt wczytany z zasobów: część systemowa, szablon części użytkownika i
 * opcjonalny schemat odpowiedzi. Wersja jest częścią tożsamości — po zmianie
 * promptu wynik jest inny, więc numer wersji musi dać się zapisać przy danych,
 * które ten prompt wyprodukował.
 */
public record Prompt(String name, String version, String system, String userTemplate,
                     Optional<JsonSchema> schema) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    public Prompt {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(userTemplate, "userTemplate");
        Objects.requireNonNull(schema, "schema");
    }

    /**
     * Wstawia wartości w miejsca {@code {{nazwa}}}. Miejsce bez wartości jest
     * błędem, nie pustym napisem: prompt z dziurą to zapytanie, za które
     * zapłacimy, a odpowiedź i tak będzie o czymś innym.
     */
    public String user(Map<String, String> variables) {

        Objects.requireNonNull(variables, "variables");
        Set<String> missing = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(userTemplate);
        String filled = matcher.replaceAll(match -> replacement(match, variables, missing));
        if (!missing.isEmpty()) {

            throw new LlmNotConfiguredException(
                    LlmMessages.PROMPT_PLACEHOLDER_MISSING.formatted(name, version, missing));
        }
        return filled;
    }

    /** Schemat wymagany przez wywołującego — brak pliku to błąd konfiguracji. */
    public JsonSchema requiredSchema() {

        return schema.orElseThrow(() -> new LlmNotConfiguredException(
                LlmMessages.PROMPT_WITHOUT_SCHEMA.formatted(name, version)));
    }

    private static String replacement(MatchResult match, Map<String, String> variables, Set<String> missing) {

        String key = match.group(1);
        String value = variables.get(key);
        if (Objects.isNull(value)) {

            missing.add(key);
            return Matcher.quoteReplacement(match.group());
        }
        return Matcher.quoteReplacement(value);
    }
}
