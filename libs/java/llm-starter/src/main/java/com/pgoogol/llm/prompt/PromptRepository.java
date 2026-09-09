package com.pgoogol.llm.prompt;

import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmNotConfiguredException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompty jako wersjonowane zasoby: {@code llm/<nazwa>/<wersja>/system.md},
 * {@code user.md} i opcjonalny {@code schema.json}.
 *
 * <p>Katalog na wersję, a nie sufiks w nazwie pliku, bo prompt i pilnujący go
 * schemat zmieniają się razem — rozdzielone leżałyby obok siebie w jednym
 * katalogu i nic nie mówiłoby, która para do siebie pasuje.
 *
 * <p>Zasoby są niezmienne w czasie życia procesu, więc wczytany prompt zostaje
 * w pamięci.
 */
public class PromptRepository {

    private static final String SYSTEM_FILE = "system.md";
    private static final String USER_FILE = "user.md";
    private static final String SCHEMA_FILE = "schema.json";

    private final String basePath;
    private final Map<String, Prompt> cache = new ConcurrentHashMap<>();

    public PromptRepository() {

        this("llm");
    }

    public PromptRepository(String basePath) {

        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    /**
     * @throws LlmNotConfiguredException gdy brakuje katalogu promptu albo
     *         któregoś z wymaganych plików
     */
    public Prompt load(String name, String version) {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        String key = "%s/%s".formatted(name, version);
        return cache.computeIfAbsent(key, ignored -> read(name, version));
    }

    private Prompt read(String name, String version) {

        String directory = "%s/%s/%s/".formatted(basePath, name, version);
        String system = requiredText(directory + SYSTEM_FILE);
        String user = requiredText(directory + USER_FILE);
        Optional<JsonSchema> schema = optionalText(directory + SCHEMA_FILE)
                .map(json -> new JsonSchema(name, json));
        return new Prompt(name, version, system, user, schema);
    }

    private String requiredText(String path) {

        return optionalText(path).orElseThrow(() ->
                new LlmNotConfiguredException(LlmMessages.PROMPT_NOT_FOUND.formatted(path)));
    }

    private Optional<String> optionalText(String path) {

        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {

            return Optional.empty();
        }
        try (InputStream stream = resource.getInputStream()) {

            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip());
        } catch (IOException ex) {

            throw new LlmNotConfiguredException(LlmMessages.PROMPT_UNREADABLE.formatted(path), ex);
        }
    }
}
