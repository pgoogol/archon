package com.pgoogol.llm.provider;

import com.pgoogol.llm.LlmClient;
import com.pgoogol.llm.LlmClients;
import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.autoconfigure.LlmProperties;
import com.pgoogol.llm.autoconfigure.LlmProvider;
import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmNotConfiguredException;
import com.pgoogol.llm.usage.LlmUsagePublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Buduje klienty z konfiguracji: po jednym {@link RestClient} na wpis, z własnymi
 * timeoutami. Brak klucza nie przeszkadza w postawieniu klienta — serwis bez
 * klucza ma wstawać i działać „bez importu", a błąd pada dopiero przy użyciu.
 */
public class LlmClientFactory {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final LlmUsagePublisher usagePublisher;

    public LlmClientFactory(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            LlmUsagePublisher usagePublisher) {

        this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "restClientBuilder");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.usagePublisher = Objects.requireNonNull(usagePublisher, "usagePublisher");
    }

    public LlmClients create(LlmProperties properties) {

        Objects.requireNonNull(properties, "properties");
        Map<String, LlmClient> clients = new LinkedHashMap<>();
        properties.getClients().forEach((name, config) -> clients.put(name, client(name, config)));
        LlmClients registry = new LlmClients(clients, properties.getDefaultClient());
        requireDeclared(properties, registry);
        return registry;
    }

    /**
     * Brakujący klient wychodzi przy starcie, a nie przy pierwszym imporcie po
     * wdrożeniu — literówka w nazwie kosztuje wtedy restart, nie zgłoszenie.
     */
    private void requireDeclared(LlmProperties properties, LlmClients registry) {

        properties.getRequiredClients().stream()
                .filter(name -> !registry.has(name))
                .findFirst()
                .ifPresent(name -> {

                    throw new LlmNotConfiguredException(LlmMessages.NO_CLIENT.formatted(name, registry.names()));
                });
    }

    private LlmClient client(String name, LlmClientProperties config) {

        RestClient restClient = restClient(name, config);
        if (Objects.equals(config.getProvider(), LlmProvider.ANTHROPIC)) {

            return new AnthropicLlmClient(name, config, restClient, objectMapper, usagePublisher);
        }
        return new OpenAiCompatibleLlmClient(name, config, restClient, objectMapper, usagePublisher);
    }

    private RestClient restClient(String name, LlmClientProperties config) {

        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(config.resolvedBaseUrl())
                .requestFactory(requestFactory(config))
                .requestInterceptor(new MaskedHeaderLoggingInterceptor(name));
        authorize(builder, config);
        return builder.build();
    }

    private void authorize(RestClient.Builder builder, LlmClientProperties config) {

        if (!config.hasApiKey()) {

            return;
        }
        if (Objects.equals(config.getProvider(), LlmProvider.ANTHROPIC)) {

            builder.defaultHeader("x-api-key", config.getApiKey())
                    .defaultHeader("anthropic-version", AnthropicLlmClient.API_VERSION);
            return;
        }
        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey());
    }

    private JdkClientHttpRequestFactory requestFactory(LlmClientProperties config) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(config.getReadTimeout());
        return factory;
    }
}
