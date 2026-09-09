package com.pgoogol.llm;

/**
 * Klient modelu językowego. Jedna instancja odpowiada jednemu wpisowi
 * {@code llm.clients.<nazwa>} — modelowi, providerowi i limitom z konfiguracji.
 *
 * <p>Implementacje są bezstanowe i bezpieczne wątkowo.
 */
public interface LlmClient {

    /**
     * Nazwa klienta z konfiguracji ({@code text}, {@code vision}). Wchodzi do
     * zdarzeń zużycia i komunikatów błędów, żeby dało się poznać, który model
     * zapłacił i który odmówił.
     */
    String name();

    /**
     * Zapytanie o swobodną odpowiedź tekstową.
     *
     * @throws com.pgoogol.llm.exception.LlmException gdy provider odmówi,
     *         przestanie odpowiadać albo zwróci coś, czego nie da się odczytać
     */
    LlmResponse complete(LlmRequest request);

    /**
     * Zapytanie o odpowiedź pilnowaną schematem JSON po stronie providera.
     * Odpowiedź ucięta limitem tokenów jest błędem, nie połowicznym wynikiem:
     * niedomknięty JSON i tak nie sparsuje się do rekordu.
     *
     * @throws com.pgoogol.llm.exception.LlmResponseException gdy odpowiedź jest
     *         ucięta albo nie daje się odwzorować na {@code type}
     */
    <T> LlmExtraction<T> extract(LlmRequest request, JsonSchema schema, Class<T> type);
}
