package com.pgoogol.llm.test;

import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.LlmClient;
import com.pgoogol.llm.LlmExtraction;
import com.pgoogol.llm.LlmRequest;
import com.pgoogol.llm.LlmResponse;
import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.StopReason;
import com.pgoogol.llm.exception.LlmErrorCodes;
import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Klient ze skryptowanymi odpowiedziami — dla testów serwisu, który używa
 * startera. Jest tu, a nie w testach startera, bo używa go kod spoza tego
 * modułu; WireMock zostaje testom providerów, gdzie chodzi o kształt żądania.
 *
 * <p>Odpowiedzi wydawane są w kolejności dodania. Rejestr żądań pozwala
 * sprawdzić, co poszło do modelu — łącznie z tym, czy prompt dostał obraz.
 */
public class FakeLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "fake-model";

    private final String name;
    private final ObjectMapper objectMapper;
    private final Deque<Object> scripted = new ArrayDeque<>();
    private final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

    public FakeLlmClient() {

        this("fake", JsonMapper.builder().build());
    }

    public FakeLlmClient(String name, ObjectMapper objectMapper) {

        this.name = Objects.requireNonNull(name, "name");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** Kolejna odpowiedź to ten tekst, bez zużycia tokenów. */
    public FakeLlmClient answerWith(String text) {

        LlmResponse response = new LlmResponse(text, StopReason.COMPLETED, LlmUsage.none(), DEFAULT_MODEL);
        return answerWith(response);
    }

    public FakeLlmClient answerWith(LlmResponse response) {

        scripted.addLast(Objects.requireNonNull(response, "response"));
        return this;
    }

    /** Kolejne wywołanie kończy się tym wyjątkiem. */
    public FakeLlmClient failWith(RuntimeException failure) {

        scripted.addLast(Objects.requireNonNull(failure, "failure"));
        return this;
    }

    public List<LlmRequest> requests() {

        return List.copyOf(requests);
    }

    public Optional<LlmRequest> lastRequest() {

        if (requests.isEmpty()) {

            return Optional.empty();
        }
        return Optional.of(requests.get(requests.size() - 1));
    }

    public void reset() {

        scripted.clear();
        requests.clear();
    }

    @Override
    public String name() {

        return name;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {

        Objects.requireNonNull(request, "request");
        requests.add(request);
        return next();
    }

    @Override
    public <T> LlmExtraction<T> extract(LlmRequest request, JsonSchema schema, Class<T> type) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(type, "type");
        LlmResponse response = complete(request);
        if (response.truncated()) {

            throw new LlmResponseException(LlmErrorCodes.RESPONSE_TRUNCATED,
                    LlmMessages.RESPONSE_TRUNCATED.formatted(name));
        }
        T value = readValue(response.text(), type);
        return new LlmExtraction<>(value, response.text(), response);
    }

    private LlmResponse next() {

        if (scripted.isEmpty()) {

            throw new NoSuchElementException("FakeLlmClient nie ma więcej zaplanowanych odpowiedzi");
        }
        Object scheduled = scripted.removeFirst();
        if (scheduled instanceof RuntimeException failure) {

            throw failure;
        }
        return (LlmResponse) scheduled;
    }

    private <T> T readValue(String json, Class<T> type) {

        try {

            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {

            throw new LlmResponseException(LlmErrorCodes.RESPONSE_INVALID,
                    LlmMessages.RESPONSE_NOT_MATCHING_SCHEMA.formatted(name, type.getSimpleName()), ex);
        }
    }
}
