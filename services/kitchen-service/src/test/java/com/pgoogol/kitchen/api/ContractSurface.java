package com.pgoogol.kitchen.api;

import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Powierzchnia API odczytana z ręcznie pisanego {@code contracts/openapi/kitchen.yaml}.
 *
 * <p>Enumy zbieramy przechodząc całe drzewo, a nie tylko {@code components.schemas}:
 * po stronie kodu springdoc wstawia je w miejscu użycia i nie nadaje im nazwy,
 * więc porównanie po nazwie byłoby nieporównywalne z założenia. Zbiór wartości
 * jest jedyną rzeczą, którą obie strony opisują tak samo.</p>
 */
final class ContractSurface {

    private final Path file;

    ContractSurface(Path file) {

        this.file = file;
    }

    ApiSurface read() throws Exception {

        Map<String, Object> root = load();
        Set<List<String>> enums = new LinkedHashSet<>();
        collectEnums(root, enums);
        return new ApiSurface(operations(root), parameters(root), schemaFields(root), enums);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load() throws Exception {

        String text = Files.readString(file);
        return (Map<String, Object>) new Yaml().load(text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paths(Map<String, Object> root) {

        return (Map<String, Object>) root.get("paths");
    }

    @SuppressWarnings("unchecked")
    private Set<String> operations(Map<String, Object> root) {

        Set<String> operations = new TreeSet<>();
        paths(root).forEach((path, item) ->
            ((Map<String, Object>) item).keySet().stream()
                .filter(ApiSurface.METHODS::contains)
                .forEach(method -> operations.add(ApiSurface.operation(method, path))));
        return operations;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> parameters(Map<String, Object> root) {

        Map<String, Object> shared = sharedParameters(root);
        Map<String, Set<String>> parameters = new TreeMap<>();
        paths(root).forEach((path, item) -> ((Map<String, Object>) item).forEach((method, spec) ->
            collectParameters(method, path, spec, shared, parameters)));
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private void collectParameters(String method, String path, Object spec,
                                   Map<String, Object> shared,
                                   Map<String, Set<String>> parameters) {

        if (!ApiSurface.METHODS.contains(method)) {

            return;
        }
        List<Map<String, Object>> declared =
            (List<Map<String, Object>>) ((Map<String, Object>) spec).get("parameters");
        Set<String> names = new TreeSet<>();
        if (declared != null) {

            declared.forEach(parameter -> names.add(parameterName(parameter, shared)));
        }
        String operation = ApiSurface.operation(method, path);
        parameters.put(operation, names);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sharedParameters(Map<String, Object> root) {

        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> shared = (Map<String, Object>) components.get("parameters");
        if (shared == null) {

            return Map.of();
        }
        return shared;
    }

    @SuppressWarnings("unchecked")
    private String parameterName(Map<String, Object> parameter, Map<String, Object> shared) {

        Object ref = parameter.get("$ref");
        if (ref instanceof String reference) {

            int slash = reference.lastIndexOf('/');
            String key = reference.substring(slash + 1);
            Map<String, Object> target = (Map<String, Object>) shared.get(key);
            return (String) target.get("name");
        }
        return (String) parameter.get("name");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> schemaFields(Map<String, Object> root) {

        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Set<String>> fields = new TreeMap<>();
        schemas.forEach((name, schema) -> {

            Map<String, Object> properties =
                (Map<String, Object>) ((Map<String, Object>) schema).get("properties");
            if (properties != null) {

                fields.put(name, new TreeSet<>(properties.keySet()));
            }
        });
        return fields;
    }

    @SuppressWarnings("unchecked")
    private void collectEnums(Object node, Set<List<String>> values) {

        if (node instanceof List<?> items) {

            items.forEach(item -> collectEnums(item, values));
            return;
        }
        if (!(node instanceof Map<?, ?> map)) {

            return;
        }
        Object constants = map.get("enum");
        if (constants instanceof List<?> list) {

            values.add(sorted((List<Object>) list));
        }
        map.values().forEach(value -> collectEnums(value, values));
    }

    private List<String> sorted(List<Object> constants) {

        Set<String> names = new TreeSet<>();
        constants.forEach(constant -> names.add(String.valueOf(constant)));
        return List.copyOf(names);
    }
}
