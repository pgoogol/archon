package com.pgoogol.music.api;

import com.pgoogol.music.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Kontrakt z {@code contracts/openapi/music.yaml} jest źródłem prawdy — ten test
 * pilnuje, żeby kod od niego nie odjechał (backend-api.md).
 *
 * <p>Porównanie idzie po <b>powierzchni API</b>: zbiór operacji (metoda + ścieżka)
 * oraz nazwy parametrów każdej z nich. Celowo NIE porównuje pól schematów bajt
 * w bajt: springdoc renderuje schematy inaczej niż pisze się je ręcznie (inne
 * nazwy typów zagnieżdżonych, inny zapis nullable), więc taka asercja pękałaby
 * przy zmianach kosmetycznych, nie przy realnym rozjeździe.</p>
 *
 * <p>Rozjazd pól łapie druga pętla: kontrakt → wygenerowane typy TS → kompilacja
 * frontu. Razem obie pokrywają to, co się faktycznie psuje.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("..", "..", "contracts", "openapi", "music.yaml");
    private static final Set<String> METHODS =
        Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("kod wystawia dokładnie te operacje, które opisuje kontrakt")
    void generatedSpec_exposesExactlyTheOperationsFromContract() throws Exception {

        // given
        Set<String> fromContract = contractOperations();

        // when
        Set<String> fromCode = generatedOperations();

        // then
        assertThat(fromCode)
            .as("operacje w kodzie kontra kontrakt — zmiana API zaczyna się od contracts/openapi/")
            .containsExactlyInAnyOrderElementsOf(fromContract);
    }

    @Test
    @DisplayName("każda operacja bierze parametry opisane w kontrakcie")
    void generatedSpec_matchesContractParameterNames() throws Exception {

        // given
        Map<String, Set<String>> fromContract = contractParameters();

        // when
        Map<String, Set<String>> fromCode = generatedParameters();

        // then
        assertThat(fromCode)
            .as("nazwy parametrów per operacja")
            .containsExactlyInAnyOrderEntriesOf(fromContract);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contract() throws Exception {

        assertThat(CONTRACT).as("plik kontraktu").exists();
        return (Map<String, Object>) new Yaml().load(Files.readString(CONTRACT));
    }

    @SuppressWarnings("unchecked")
    private Set<String> contractOperations() throws Exception {

        Map<String, Object> paths = (Map<String, Object>) contract().get("paths");
        Set<String> operations = new TreeSet<>();
        paths.forEach((path, item) ->
            ((Map<String, Object>) item).keySet().stream()
                .filter(METHODS::contains)
                .forEach(method -> operations.add(operation(method, path))));
        return operations;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> contractParameters() throws Exception {

        Map<String, Object> root = contract();
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        Map<String, Object> shared = sharedParameters(root);
        Map<String, Set<String>> parameters = new java.util.TreeMap<>();
        paths.forEach((path, item) -> ((Map<String, Object>) item).forEach((method, spec) -> {

            if (!METHODS.contains(method)) {

                return;
            }
            List<Map<String, Object>> declared =
                (List<Map<String, Object>>) ((Map<String, Object>) spec).get("parameters");
            Set<String> names = new LinkedHashSet<>();
            if (declared != null) {

                declared.forEach(parameter -> names.add(parameterName(parameter, shared)));
            }
            parameters.put(operation(method, path), new TreeSet<>(names));
        }));
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sharedParameters(Map<String, Object> root) {

        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> shared = (Map<String, Object>) components.get("parameters");
        return shared == null ? Map.of() : shared;
    }

    @SuppressWarnings("unchecked")
    private String parameterName(Map<String, Object> parameter, Map<String, Object> shared) {

        Object ref = parameter.get("$ref");
        if (ref instanceof String reference) {

            String key = reference.substring(reference.lastIndexOf('/') + 1);
            return (String) ((Map<String, Object>) shared.get(key)).get("name");
        }
        return (String) parameter.get("name");
    }

    private JsonNode generatedSpec() throws Exception {

        String body = mockMvc.perform(get("/v3/api-docs"))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Set<String> generatedOperations() throws Exception {

        JsonNode paths = generatedSpec().get("paths");
        Set<String> operations = new TreeSet<>();
        paths.properties().forEach(path -> path.getValue().properties().forEach(method -> {

            if (METHODS.contains(method.getKey())) {

                operations.add(operation(method.getKey(), path.getKey()));
            }
        }));
        return operations;
    }

    private Map<String, Set<String>> generatedParameters() throws Exception {

        JsonNode paths = generatedSpec().get("paths");
        Map<String, Set<String>> parameters = new java.util.TreeMap<>();
        paths.properties().forEach(path -> path.getValue().properties().forEach(method -> {

            if (!METHODS.contains(method.getKey())) {

                return;
            }
            Set<String> names = new TreeSet<>();
            JsonNode declared = method.getValue().get("parameters");
            if (declared != null) {

                declared.forEach(parameter -> names.add(parameter.get("name").asString()));
            }
            parameters.put(operation(method.getKey(), path.getKey()), names);
        }));
        return parameters;
    }

    private String operation(String method, String path) {

        return method.toUpperCase(java.util.Locale.ROOT) + " " + path;
    }
}
