package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmUnsupportedInputException;

import java.util.Objects;
import java.util.Optional;

/**
 * Nadpisania konfiguracji dla jednego zapytania. {@code null} znaczy „nie
 * ustawiono" i wtedy wchodzi wartość z {@code llm.clients.<nazwa>}.
 *
 * <p>{@code temperature} celowo jest obiektem, nie {@code double}: nowsze modele
 * odrzucają ten parametr błędem 400, więc „nie wysyłamy go wcale" musi być
 * odróżnialne od „wysyłamy zero".
 */
public record LlmOptions(Integer maxTokens, Effort effort, Double temperature) {

    private static final LlmOptions UNSET = new LlmOptions(null, null, null);

    public LlmOptions {

        if (Objects.nonNull(maxTokens) && maxTokens <= 0) {

            throw new LlmUnsupportedInputException(LlmMessages.NON_POSITIVE_MAX_TOKENS.formatted(maxTokens));
        }
        if (Objects.nonNull(temperature) && (temperature < 0.0 || temperature > 2.0)) {

            throw new LlmUnsupportedInputException(LlmMessages.TEMPERATURE_OUT_OF_RANGE.formatted(temperature));
        }
    }

    public static LlmOptions unset() {

        return UNSET;
    }

    public static LlmOptions maxTokens(int maxTokens) {

        return new LlmOptions(maxTokens, null, null);
    }

    public LlmOptions withMaxTokens(int value) {

        return new LlmOptions(value, effort, temperature);
    }

    public LlmOptions withEffort(Effort value) {

        return new LlmOptions(maxTokens, value, temperature);
    }

    public LlmOptions withTemperature(double value) {

        return new LlmOptions(maxTokens, effort, value);
    }

    public Optional<Integer> maxTokensValue() {

        return Optional.ofNullable(maxTokens);
    }

    public Optional<Effort> effortValue() {

        return Optional.ofNullable(effort);
    }

    public Optional<Double> temperatureValue() {

        return Optional.ofNullable(temperature);
    }
}
