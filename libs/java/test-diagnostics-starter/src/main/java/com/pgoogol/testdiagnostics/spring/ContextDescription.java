package com.pgoogol.testdiagnostics.spring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Konfiguracja kontekstu testów jako nazwane listy tekstów. Dwa opisy są równe wtedy,
 * gdy Spring uznałby konfiguracje za równe i użył tego samego kontekstu z pamięci
 * podręcznej; różnice między opisami to powód nowego środowiska w raporcie.
 *
 * @param attributes atrybut → wartości, w stałej kolejności atrybutów
 */
record ContextDescription(Map<String, List<String>> attributes) {

    static final String CLASSES = "classes";

    static final String LOCATIONS = "locations";

    static final String PROFILES = "profiles";

    static final String PROPERTY_SOURCES = "propertySources";

    static final String PROPERTIES = "properties";

    static final String BEAN_OVERRIDES = "beanOverrides";

    static final String DYNAMIC_PROPERTIES = "dynamicProperties";

    static final String CUSTOMIZERS = "customizers";

    static final String INITIALIZERS = "initializers";

    static final String LOADER = "loader";

    static final String WEB = "web";

    static final String PARENT = "parent";

    ContextDescription {

        Map<String, List<String>> copy = new LinkedHashMap<>();
        attributes.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        attributes = Collections.unmodifiableMap(copy);
    }

    List<String> values(String attribute) {

        return attributes.getOrDefault(attribute, List.of());
    }

    Set<String> names() {

        return attributes.keySet();
    }
}
