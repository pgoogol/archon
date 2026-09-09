package com.pgoogol.llm.autoconfigure;

import com.pgoogol.llm.Effort;

import java.time.Duration;
import java.util.Objects;

/**
 * Konfiguracja jednego nazwanego klienta ({@code llm.clients.<nazwa>}).
 *
 * <p>{@code temperature} i {@code effort} są obiektami, żeby dało się ich nie
 * ustawiać wcale: model bez trybu rozumowania odrzuca {@code effort}, a nowsze
 * modele odrzucają {@code temperature} — wysłanie wartości domyślnej byłoby
 * błędem 400 zamiast odpowiedzi.
 */
public class LlmClientProperties {

    private LlmProvider provider = LlmProvider.ANTHROPIC;

    private String apiKey;

    private String baseUrl;

    private String model;

    private int maxTokens = 4096;

    private Double temperature;

    private Effort effort;

    private int requestsPerSecond = 1;

    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * Odpowiedź z długiego promptu potrafi schodzić minutami — krótszy limit
     * zrywałby połączenia, za które i tak już zapłaciliśmy.
     */
    private Duration readTimeout = Duration.ofMinutes(2);

    private int maxAttempts = 3;

    private Duration initialBackoff = Duration.ofSeconds(1);

    /**
     * Powyżej tego progu {@code Retry-After} nie jest odczekiwany, tylko zgłoszony
     * wywołującemu. Wyczerpana kwota dobowa wraca z „ponów za 22 godziny" —
     * odczekanie tego w miejscu to wątek stojący do jutra.
     */
    private Duration maxHonoredRetryAfter = Duration.ofSeconds(30);

    /** Adres providera albo jego wartość domyślna dla wybranej rodziny API. */
    public String resolvedBaseUrl() {

        if (Objects.isNull(baseUrl) || baseUrl.isBlank()) {

            return provider.getDefaultBaseUrl();
        }
        return baseUrl;
    }

    public boolean hasApiKey() {

        return Objects.nonNull(apiKey) && !apiKey.isBlank();
    }

    public LlmProvider getProvider() {

        return provider;
    }

    public void setProvider(LlmProvider provider) {

        this.provider = provider;
    }

    public String getApiKey() {

        return apiKey;
    }

    public void setApiKey(String apiKey) {

        this.apiKey = apiKey;
    }

    public String getBaseUrl() {

        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {

        this.baseUrl = baseUrl;
    }

    public String getModel() {

        return model;
    }

    public void setModel(String model) {

        this.model = model;
    }

    public int getMaxTokens() {

        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {

        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {

        return temperature;
    }

    public void setTemperature(Double temperature) {

        this.temperature = temperature;
    }

    public Effort getEffort() {

        return effort;
    }

    public void setEffort(Effort effort) {

        this.effort = effort;
    }

    public int getRequestsPerSecond() {

        return requestsPerSecond;
    }

    public void setRequestsPerSecond(int requestsPerSecond) {

        this.requestsPerSecond = requestsPerSecond;
    }

    public Duration getConnectTimeout() {

        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {

        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {

        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {

        this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {

        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {

        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {

        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {

        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxHonoredRetryAfter() {

        return maxHonoredRetryAfter;
    }

    public void setMaxHonoredRetryAfter(Duration maxHonoredRetryAfter) {

        this.maxHonoredRetryAfter = maxHonoredRetryAfter;
    }
}
