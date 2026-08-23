package com.pgoogol.finance.api;

import com.pgoogol.finance.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Kontrakt z {@code contracts/openapi/finance.yaml} jest źródłem prawdy — ten test
 * pilnuje, żeby kod od niego nie odjechał.
 *
 * <p>Porównanie idzie po <b>powierzchni API</b> na czterech poziomach: zbiór
 * operacji (metoda + ścieżka), nazwy parametrów każdej z nich, <b>nazwy pól
 * każdego schematu</b> i <b>wartości każdego enuma</b>.</p>
 *
 * <p>Pola i enumy porównujemy wyłącznie po <b>nazwach</b>, nigdy po typie, opisie
 * czy kolejności. To rozróżnienie jest celowe: springdoc renderuje typy i opisy
 * inaczej, niż pisze się je ręcznie, więc porównanie bajt w bajt pękałoby przy
 * zmianach kosmetycznych. Zbiór nazw pól jest niezależny od sposobu renderowania,
 * a to on stanowi kontrakt dla klienta.</p>
 *
 * <p>Ten poziom powstał po realnym rozjeździe: {@code UpcomingItem.type} było
 * zwracane przez kod i nieobecne w kontrakcie, a test porównujący wyłącznie
 * operacje i parametry przepuścił to bez słowa. Wyłapały to dopiero wygenerowane
 * typy TS użyte w teście frontu — czyli dwie warstwy dalej, w innym module.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("..", "..", "contracts", "openapi", "finance.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    /**
     * Springdoc nazywa schemat typu generycznego, sklejając nazwę surowego typu
     * z nazwą argumentu: {@code PageResponse<TransactionResponse>} wychodzi jako
     * {@code PageResponseTransactionResponse}. Kontrakt pisany ręcznie nazywa to
     * po ludzku. To różnica w renderowaniu, nie w API — ale wpisujemy ją jawnie,
     * żeby kolejny typ generyczny zatrzymał build i wymusił świadomą decyzję,
     * zamiast po cichu wypaść z porównania.
     */
    private static final Map<String, String> SCHEMA_NAME_ALIASES =
        Map.of("PageResponseTransactionResponse", "TransactionPageResponse");

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

    @Test
    @DisplayName("każdy schemat ma dokładnie te pola, które opisuje kontrakt")
    void generatedSpec_matchesContractSchemaFields() throws Exception {

        // given
        Map<String, Set<String>> fromContract = contractSchemaFields();

        // when
        Map<String, Set<String>> fromCode = generatedSchemaFields();

        // then
        assertThat(fromCode)
            .as("pola schematów — nowe pole w kodzie musi wejść do contracts/openapi/")
            .containsExactlyInAnyOrderEntriesOf(fromContract);
    }

    /**
     * Enumów nie da się porównać po nazwie: kontrakt trzyma je jako nazwane
     * schematy i odwołuje się do nich przez {@code $ref}, a springdoc wstawia je
     * w miejscu użycia, bez nazwy. Porównujemy więc <b>zbiory zbiorów wartości</b>,
     * niezależnie od tego, jak enum się nazywa i gdzie występuje.
     *
     * <p>Cena: dwa enumy o identycznym zestawie stałych zlewają się w jeden wpis.
     * Nie powoduje to fałszywego alarmu, a dopisanie stałej po którejkolwiek
     * stronie i tak zmienia zbiór i zostaje wyłapane.</p>
     */
    @Test
    @DisplayName("każdy enum ma dokładnie te wartości, które opisuje kontrakt")
    void generatedSpec_matchesContractEnumValues() throws Exception {

        // given
        Set<List<String>> fromContract = contractEnumValues();

        // when
        Set<List<String>> fromCode = generatedEnumValues();

        // then
        assertThat(fromCode)
            .as("wartości enumów — nowa stała w kodzie musi wejść do contracts/openapi/")
            .containsExactlyInAnyOrderElementsOf(fromContract);
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
        Map<String, Set<String>> parameters = new TreeMap<>();
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> contractSchemas() throws Exception {

        Map<String, Object> root = contract();
        Map<String, Object> components = (Map<String, Object>) root.get("components");
        return (Map<String, Object>) components.get("schemas");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> contractSchemaFields() throws Exception {

        Map<String, Set<String>> fields = new TreeMap<>();
        contractSchemas().forEach((name, schema) -> {

            Map<String, Object> properties =
                (Map<String, Object>) ((Map<String, Object>) schema).get("properties");
            if (properties != null) {

                fields.put(name, new TreeSet<>(properties.keySet()));
            }
        });
        return fields;
    }

    private Set<List<String>> contractEnumValues() throws Exception {

        Set<List<String>> values = new LinkedHashSet<>();
        collectContractEnums(contract(), values);
        return values;
    }

    @SuppressWarnings("unchecked")
    private void collectContractEnums(Object node, Set<List<String>> values) {

        if (node instanceof List<?> items) {

            items.forEach(item -> collectContractEnums(item, values));
            return;
        }
        if (!(node instanceof Map<?, ?> map)) {

            return;
        }
        Object constants = map.get("enum");
        if (constants instanceof List<?> list) {

            values.add(sortedStrings((List<Object>) list));
        }
        map.values().forEach(value -> collectContractEnums(value, values));
    }

    private List<String> sortedStrings(List<Object> constants) {

        Set<String> names = new TreeSet<>();
        constants.forEach(constant -> names.add(String.valueOf(constant)));
        return List.copyOf(names);
    }

    private Map<String, Set<String>> generatedSchemaFields() throws Exception {

        Map<String, Set<String>> fields = new TreeMap<>();
        generatedSchemas().forEach(schema -> {

            JsonNode properties = schema.getValue().get("properties");
            if (Objects.nonNull(properties)) {

                String name = schemaName(schema.getKey());
                fields.put(name, new TreeSet<>(properties.propertyNames()));
            }
        });
        return fields;
    }

    private String schemaName(String generated) {

        return SCHEMA_NAME_ALIASES.getOrDefault(generated, generated);
    }

    private Set<List<String>> generatedEnumValues() throws Exception {

        Set<List<String>> values = new LinkedHashSet<>();
        collectGeneratedEnums(generatedSpec(), values);
        return values;
    }

    private void collectGeneratedEnums(JsonNode node, Set<List<String>> values) {

        JsonNode constants = node.get("enum");
        if (Objects.nonNull(constants)) {

            Set<String> names = new TreeSet<>();
            constants.forEach(constant -> names.add(constant.asString()));
            values.add(List.copyOf(names));
        }
        node.forEach(child -> collectGeneratedEnums(child, values));
    }

    private List<Map.Entry<String, JsonNode>> generatedSchemas() throws Exception {

        JsonNode components = generatedSpec().get("components");
        JsonNode schemas = components.get("schemas");
        return schemas.properties().stream().toList();
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
        Map<String, Set<String>> parameters = new TreeMap<>();
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

        return method.toUpperCase(Locale.ROOT) + " " + path;
    }
}
