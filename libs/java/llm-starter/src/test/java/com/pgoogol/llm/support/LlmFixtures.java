package com.pgoogol.llm.support;

import com.pgoogol.llm.LlmClient;
import com.pgoogol.llm.LlmClients;
import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.autoconfigure.LlmProperties;
import com.pgoogol.llm.autoconfigure.LlmProvider;
import com.pgoogol.llm.provider.LlmClientFactory;
import com.pgoogol.llm.usage.CostEstimator;
import com.pgoogol.llm.usage.LlmUsageListener;
import com.pgoogol.llm.usage.LlmUsagePublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gotowe konfiguracje i klienty dla testów. Backoff jest tu skrócony do
 * milisekund — z produkcyjną sekundą test ponowień stałby kilka sekund i nic
 * by z tego nie wynikało.
 */
public final class LlmFixtures {

    public static final String API_KEY = "test-api-key-0123456789";
    public static final String MODEL = "test-model";

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private LlmFixtures() {

    }

    public static ObjectMapper objectMapper() {

        return OBJECT_MAPPER;
    }

    public static LlmClientProperties config(LlmProvider provider, String baseUrl) {

        LlmClientProperties config = new LlmClientProperties();
        config.setProvider(provider);
        config.setBaseUrl(baseUrl);
        config.setApiKey(API_KEY);
        config.setModel(MODEL);
        config.setMaxTokens(1024);
        config.setRequestsPerSecond(50);
        config.setInitialBackoff(Duration.ofMillis(10));
        config.setMaxHonoredRetryAfter(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(5));
        return config;
    }

    public static LlmClient client(String name, LlmClientProperties config) {

        return client(name, config, publisher(List.of()));
    }

    public static LlmClient client(String name, LlmClientProperties config, LlmUsagePublisher publisher) {

        LlmProperties properties = new LlmProperties();
        properties.setClients(Map.of(name, config));
        LlmClientFactory factory = new LlmClientFactory(RestClient.builder(), OBJECT_MAPPER, publisher);
        LlmClients clients = factory.create(properties);
        return clients.client(name);
    }

    public static LlmUsagePublisher publisher(List<LlmUsageListener> listeners) {

        CostEstimator estimator = new CostEstimator(Map.of());
        return new LlmUsagePublisher(listeners, estimator);
    }
}
