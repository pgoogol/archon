package com.pgoogol.kitchen.api;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Powierzchnia API odczytana ze specyfikacji, którą springdoc generuje z kodu
 * pod {@code /v3/api-docs}.
 */
final class GeneratedSurface {

    /**
     * Springdoc nazywa schemat typu generycznego, sklejając nazwę surowego typu
     * z nazwą argumentu: {@code PageResponse<RecipeResponse>} wychodzi jako
     * {@code PageResponseRecipeResponse}. Kontrakt pisany ręcznie nazywa to po
     * ludzku. To różnica w renderowaniu, nie w API — ale wpisujemy ją jawnie,
     * żeby kolejny typ generyczny zatrzymał build i wymusił świadomą decyzję,
     * zamiast po cichu wypaść z porównania. Pusta, dopóki API nie ma stronicowania.
     */
    private static final Map<String, String> NAME_ALIASES = Map.of();

    private final JsonNode spec;

    GeneratedSurface(JsonNode spec) {

        this.spec = spec;
    }

    ApiSurface read() {

        Set<List<String>> enums = new LinkedHashSet<>();
        collectEnums(spec, enums);
        return new ApiSurface(operations(), parameters(), schemaFields(), enums);
    }

    private Set<String> operations() {

        Set<String> operations = new TreeSet<>();
        paths().forEach(path -> path.getValue().properties().forEach(method ->
            collectOperation(method.getKey(), path.getKey(), operations)));
        return operations;
    }

    private void collectOperation(String method, String path, Set<String> operations) {

        if (!ApiSurface.METHODS.contains(method)) {

            return;
        }
        String operation = ApiSurface.operation(method, path);
        operations.add(operation);
    }

    private Map<String, Set<String>> parameters() {

        Map<String, Set<String>> parameters = new TreeMap<>();
        paths().forEach(path -> path.getValue().properties().forEach(method ->
            collectParameters(method, path.getKey(), parameters)));
        return parameters;
    }

    private void collectParameters(Map.Entry<String, JsonNode> method, String path,
                                   Map<String, Set<String>> parameters) {

        if (!ApiSurface.METHODS.contains(method.getKey())) {

            return;
        }
        Set<String> names = new TreeSet<>();
        JsonNode declared = method.getValue().get("parameters");
        if (Objects.nonNull(declared)) {

            declared.forEach(parameter -> names.add(parameter.get("name").asString()));
        }
        String operation = ApiSurface.operation(method.getKey(), path);
        parameters.put(operation, names);
    }

    private Map<String, Set<String>> schemaFields() {

        Map<String, Set<String>> fields = new TreeMap<>();
        schemas().forEach(schema -> collectFields(schema, fields));
        return fields;
    }

    private void collectFields(Map.Entry<String, JsonNode> schema,
                               Map<String, Set<String>> fields) {

        JsonNode properties = schema.getValue().get("properties");
        if (Objects.isNull(properties)) {

            return;
        }
        String name = NAME_ALIASES.getOrDefault(schema.getKey(), schema.getKey());
        fields.put(name, new TreeSet<>(properties.propertyNames()));
    }

    private void collectEnums(JsonNode node, Set<List<String>> values) {

        JsonNode constants = node.get("enum");
        if (Objects.nonNull(constants)) {

            Set<String> names = new TreeSet<>();
            constants.forEach(constant -> names.add(constant.asString()));
            values.add(List.copyOf(names));
        }
        node.forEach(child -> collectEnums(child, values));
    }

    private List<Map.Entry<String, JsonNode>> paths() {

        JsonNode paths = spec.get("paths");
        return paths.properties().stream().toList();
    }

    private List<Map.Entry<String, JsonNode>> schemas() {

        JsonNode components = spec.get("components");
        JsonNode schemas = components.get("schemas");
        return schemas.properties().stream().toList();
    }
}
