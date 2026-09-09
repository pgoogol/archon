package com.pgoogol.llm.provider;

import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.LlmClient;
import com.pgoogol.llm.LlmExtraction;
import com.pgoogol.llm.LlmRequest;
import com.pgoogol.llm.LlmResponse;
import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.exception.LlmErrorCodes;
import com.pgoogol.llm.exception.LlmException;
import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmNotConfiguredException;
import com.pgoogol.llm.exception.LlmRateLimitedException;
import com.pgoogol.llm.exception.LlmRequestRejectedException;
import com.pgoogol.llm.exception.LlmResponseException;
import com.pgoogol.llm.exception.LlmUnavailableException;
import com.pgoogol.llm.usage.LlmUsagePublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Wspólna część obu providerów: sprawdzenie konfiguracji, wywołanie przez
 * limiter, przełożenie błędów HTTP na wyjątki startera i publikacja zużycia.
 * Providery dokładają wyłącznie kształt żądania i odczyt odpowiedzi.
 */
public abstract class AbstractHttpLlmClient implements LlmClient {

    /** Ile treści odpowiedzi błędu wchodzi do komunikatu — reszta to szum w logu. */
    private static final int ERROR_BODY_LIMIT = 500;

    protected final ObjectMapper objectMapper;

    private final String name;
    private final LlmClientProperties config;
    private final RestClient restClient;
    private final LlmCallGuard guard;
    private final LlmUsagePublisher usagePublisher;
    private final SecretMasker secretMasker;

    protected AbstractHttpLlmClient(String name,
                                    LlmClientProperties config,
                                    RestClient restClient,
                                    ObjectMapper objectMapper,
                                    LlmUsagePublisher usagePublisher) {

        this.name = Objects.requireNonNull(name, "name");
        this.config = Objects.requireNonNull(config, "config");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.usagePublisher = Objects.requireNonNull(usagePublisher, "usagePublisher");
        this.guard = new LlmCallGuard(name, config);
        this.secretMasker = new SecretMasker(config.getApiKey());
    }

    @Override
    public String name() {

        return name;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {

        return call(request, null);
    }

    @Override
    public <T> LlmExtraction<T> extract(LlmRequest request, JsonSchema schema, Class<T> type) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(type, "type");
        LlmResponse response = call(request, schema);
        if (response.truncated()) {

            throw new LlmResponseException(LlmErrorCodes.RESPONSE_TRUNCATED,
                    LlmMessages.RESPONSE_TRUNCATED.formatted(name));
        }
        T value = readValue(response.text(), type);
        return new LlmExtraction<>(value, response.text(), response);
    }

    /** Ścieżka zasobu providera względem jego adresu bazowego. */
    protected abstract String path();

    /** Ciało żądania; {@code schema} jest puste dla zwykłego uzupełnienia tekstu. */
    protected abstract ObjectNode buildBody(LlmRequest request, JsonSchema schema, LlmClientProperties config);

    /** Odczyt odpowiedzi providera do wspólnej postaci. */
    protected abstract LlmResponse parse(JsonNode payload);

    private LlmResponse call(LlmRequest request, JsonSchema schema) {

        Objects.requireNonNull(request, "request");
        requireConfigured();
        ObjectNode body = buildBody(request, schema, config);
        long startedAt = System.nanoTime();
        JsonNode payload = guard.execute(() -> post(body));
        LlmResponse response = parse(payload);
        Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
        usagePublisher.publish(name, response, duration);
        return response;
    }

    private void requireConfigured() {

        if (!config.hasApiKey()) {

            throw new LlmNotConfiguredException(LlmMessages.MISSING_API_KEY.formatted(name, name));
        }
        if (Objects.isNull(config.getModel()) || config.getModel().isBlank()) {

            throw new LlmNotConfiguredException(LlmMessages.MISSING_MODEL.formatted(name, name));
        }
    }

    private JsonNode post(ObjectNode body) {

        try {

            JsonNode payload = restClient.post()
                    .uri(path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (Objects.isNull(payload) || payload.isEmpty()) {

                throw new LlmResponseException(LlmErrorCodes.RESPONSE_EMPTY,
                        LlmMessages.RESPONSE_EMPTY.formatted(name));
            }
            return payload;
        } catch (RestClientResponseException ex) {

            throw translate(ex);
        } catch (ResourceAccessException ex) {

            throw new LlmUnavailableException(LlmMessages.UNAVAILABLE.formatted(name), ex);
        } catch (RestClientException ex) {

            throw new LlmResponseException(LlmErrorCodes.RESPONSE_INVALID,
                    LlmMessages.RESPONSE_UNREADABLE.formatted(name), ex);
        }
    }

    private LlmException translate(RestClientResponseException ex) {

        int status = ex.getStatusCode().value();
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {

            return new LlmRateLimitedException(LlmMessages.RATE_LIMITED.formatted(name), retryAfter(ex), ex);
        }
        if (ex.getStatusCode().is5xxServerError()) {

            return new LlmUnavailableException(LlmMessages.UNAVAILABLE.formatted(name), ex);
        }
        String detail = secretMasker.mask(ex.getResponseBodyAsString());
        return new LlmRequestRejectedException(
                LlmMessages.REQUEST_REJECTED.formatted(name, status, truncate(detail)), status, ex);
    }

    private Duration retryAfter(RestClientResponseException ex) {

        HttpHeaders headers = ex.getResponseHeaders();
        if (Objects.isNull(headers)) {

            return null;
        }
        String header = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (Objects.isNull(header)) {

            return null;
        }
        try {

            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException ignored) {

            // nagłówek w postaci daty HTTP — o przerwie decyduje wtedy backoff
            return null;
        }
    }

    private <T> T readValue(String json, Class<T> type) {

        try {

            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {

            throw new LlmResponseException(LlmErrorCodes.RESPONSE_INVALID,
                    LlmMessages.RESPONSE_NOT_MATCHING_SCHEMA.formatted(name, type.getSimpleName()), ex);
        }
    }

    private String truncate(String text) {

        if (text.length() <= ERROR_BODY_LIMIT) {

            return text;
        }
        return text.substring(0, ERROR_BODY_LIMIT) + "…";
    }

    /** Tekst z odpowiedzi albo błąd — pusta odpowiedź nie jest wynikiem. */
    protected String requireText(Optional<String> text) {

        return text.filter(value -> !value.isBlank())
                .orElseThrow(() -> new LlmResponseException(LlmErrorCodes.RESPONSE_EMPTY,
                        LlmMessages.RESPONSE_EMPTY.formatted(name)));
    }
}
