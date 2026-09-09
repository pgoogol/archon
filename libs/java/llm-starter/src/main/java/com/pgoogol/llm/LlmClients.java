package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmNotConfiguredException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Nazwane klienty z konfiguracji. Serwis prosi o {@code client("vision")}, a nie
 * o „ten z wizją" — wybór modelu należy do konfiguracji wdrożenia, nie do kodu.
 */
public class LlmClients {

    private final Map<String, LlmClient> clients;
    private final String defaultName;

    public LlmClients(Map<String, LlmClient> clients, String defaultName) {

        Objects.requireNonNull(clients, "clients");
        this.clients = new LinkedHashMap<>(clients);
        this.defaultName = defaultName;
    }

    /**
     * @throws LlmNotConfiguredException gdy w konfiguracji nie ma klienta o tej nazwie
     */
    public LlmClient client(String name) {

        Objects.requireNonNull(name, "name");
        LlmClient client = clients.get(name);
        if (Objects.isNull(client)) {

            throw new LlmNotConfiguredException(LlmMessages.NO_CLIENT.formatted(name, names()));
        }
        return client;
    }

    /**
     * Klient wskazany w {@code llm.default-client}, a przy jednym skonfigurowanym
     * po prostu ten jeden.
     */
    public LlmClient defaultClient() {

        if (Objects.nonNull(defaultName)) {

            return client(defaultName);
        }
        if (clients.isEmpty()) {

            throw new LlmNotConfiguredException(LlmMessages.NO_DEFAULT_CLIENT);
        }
        if (clients.size() > 1) {

            throw new LlmNotConfiguredException(LlmMessages.AMBIGUOUS_DEFAULT_CLIENT.formatted(names()));
        }
        return clients.values().iterator().next();
    }

    public Set<String> names() {

        return Set.copyOf(clients.keySet());
    }

    public boolean has(String name) {

        return clients.containsKey(name);
    }
}
