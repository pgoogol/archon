package com.pgoogol.llm.provider;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.llm.Effort;
import com.pgoogol.llm.ImagePart;
import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.LlmClient;
import com.pgoogol.llm.LlmExtraction;
import com.pgoogol.llm.LlmOptions;
import com.pgoogol.llm.LlmRequest;
import com.pgoogol.llm.LlmResponse;
import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.StopReason;
import com.pgoogol.llm.TextPart;
import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.autoconfigure.LlmProvider;
import com.pgoogol.llm.exception.LlmErrorCodes;
import com.pgoogol.llm.exception.LlmNotConfiguredException;
import com.pgoogol.llm.exception.LlmRequestRejectedException;
import com.pgoogol.llm.exception.LlmResponseException;
import com.pgoogol.llm.exception.LlmUnavailableException;
import com.pgoogol.llm.support.LlmFixtures;
import com.pgoogol.llm.support.LlmWireMockStubs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.pgoogol.llm.support.LlmWireMockStubs.ANTHROPIC_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

@WireMockTest
class AnthropicLlmClientTest {

    private static final byte[] IMAGE = "udawany-jpeg".getBytes(StandardCharsets.UTF_8);

    @Test
    void complete_whenProviderResponds_returnsTextStopReasonAndUsage(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("Sernik z rodzynkami");
        LlmClient client = client(wireMock);

        // when
        LlmResponse response = client.complete(LlmRequest.text("system", "user"));

        // then
        assertThat(response.text()).isEqualTo("Sernik z rodzynkami");
        assertThat(response.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(response.model()).isEqualTo("test-model-20260101");
        assertThat(response.usage()).isEqualTo(new LlmUsage(120, 30, 10, 5));
        verify(postRequestedFor(urlPathEqualTo(ANTHROPIC_PATH))
                .withHeader("x-api-key", equalTo(LlmFixtures.API_KEY))
                .withHeader("anthropic-version", equalTo(AnthropicLlmClient.API_VERSION)));
    }

    @Test
    void complete_whenTemperatureNotSet_omitsItFromRequest(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("odpowiedź");
        LlmClient client = client(wireMock);

        // when
        client.complete(LlmRequest.text("system", "user"));

        // then: nowsze modele odrzucają ten parametr błędem, więc ma go nie być wcale
        JsonNode body = LlmWireMockStubs.lastRequestBody(ANTHROPIC_PATH);
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("output_config")).isFalse();
        assertThat(body.path("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void complete_whenTemperatureSetOnRequest_sendsIt(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("odpowiedź");
        LlmClient client = client(wireMock);
        LlmRequest request = LlmRequest.text("system", "user")
                .withOptions(LlmOptions.unset().withTemperature(0.4).withEffort(Effort.HIGH));

        // when
        client.complete(request);

        // then
        JsonNode body = LlmWireMockStubs.lastRequestBody(ANTHROPIC_PATH);
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.4);
        assertThat(body.path("output_config").path("effort").asString()).isEqualTo("high");
    }

    @Test
    void complete_whenRequestCarriesImage_sendsBase64WithMediaType(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("odpowiedź");
        LlmClient client = client(wireMock);
        LlmRequest request = LlmRequest.of("system", new TextPart("co to za danie?"),
                new ImagePart(IMAGE, "image/jpeg"));

        // when
        client.complete(request);

        // then
        JsonNode image = LlmWireMockStubs.lastRequestBody(ANTHROPIC_PATH)
                .path("messages").path(0).path("content").path(1);
        assertThat(image.path("type").asString()).isEqualTo("image");
        assertThat(image.path("source").path("media_type").asString()).isEqualTo("image/jpeg");
        assertThat(image.path("source").path("data").asString())
                .isEqualTo(Base64.getEncoder().encodeToString(IMAGE));
    }

    @Test
    void extract_whenSchemaGiven_sendsFormatAndParsesRecord(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("{\"title\":\"Sernik\",\"servings\":12}");
        LlmClient client = client(wireMock);
        JsonSchema schema = new JsonSchema("recipe", "{\"type\":\"object\"}");

        // when
        LlmExtraction<Dish> extraction = client.extract(LlmRequest.text("system", "user"), schema, Dish.class);

        // then
        assertThat(extraction.value()).isEqualTo(new Dish("Sernik", 12));
        JsonNode format = LlmWireMockStubs.lastRequestBody(ANTHROPIC_PATH).path("output_config").path("format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("name").asString()).isEqualTo("recipe");
        assertThat(format.path("schema").path("type").asString()).isEqualTo("object");
    }

    @Test
    void extract_whenResponseTruncated_throwsInsteadOfParsingHalfJson(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicTruncated("{\"title\":\"Ser");
        LlmClient client = client(wireMock);
        JsonSchema schema = new JsonSchema("recipe", "{\"type\":\"object\"}");
        LlmRequest request = LlmRequest.text("system", "user");

        // when, then
        assertThatThrownBy(() -> client.extract(request, schema, Dish.class))
                .isInstanceOf(LlmResponseException.class)
                .hasFieldOrPropertyWithValue("errorCode", LlmErrorCodes.RESPONSE_TRUNCATED);
    }

    @Test
    void complete_whenProviderRejectsRequest_reportsStatusWithoutApiKey(WireMockRuntimeInfo wireMock) {

        // given: provider odbija żądanie i odsyła klucz w treści błędu
        LlmWireMockStubs.badRequest(ANTHROPIC_PATH,
                "{\"error\":\"nieznany klucz " + LlmFixtures.API_KEY + "\"}");
        LlmClient client = client(wireMock);
        LlmRequest request = LlmRequest.text("system", "user");

        // when, then
        assertThatThrownBy(() -> client.complete(request))
                .isInstanceOf(LlmRequestRejectedException.class)
                .hasFieldOrPropertyWithValue("status", 400)
                .hasMessageContaining("***")
                .hasMessageNotContaining(LlmFixtures.API_KEY);
    }

    @Test
    void complete_whenProviderRateLimitsOnce_waitsRetryAfterAndSucceeds(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.rateLimitedThenAnthropicAnswers("po ponowieniu", 1);
        LlmClient client = client(wireMock);

        // when
        LlmResponse response = client.complete(LlmRequest.text("system", "user"));

        // then
        assertThat(response.text()).isEqualTo("po ponowieniu");
        assertThat(LlmWireMockStubs.requestCount(ANTHROPIC_PATH)).isEqualTo(2);
    }

    @Test
    void complete_whenProviderKeepsFailing_throwsUnavailableAfterAllAttempts(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.serverError(ANTHROPIC_PATH);
        LlmClient client = client(wireMock);
        LlmRequest request = LlmRequest.text("system", "user");

        // when, then
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmUnavailableException.class);
        assertThat(LlmWireMockStubs.requestCount(ANTHROPIC_PATH)).isEqualTo(3);
    }

    @Test
    void complete_whenApiKeyMissing_failsBeforeCallingProvider(WireMockRuntimeInfo wireMock) {

        // given: serwis bez klucza wstaje normalnie, dopiero użycie jest błędem
        LlmClientProperties config = LlmFixtures.config(LlmProvider.ANTHROPIC, wireMock.getHttpBaseUrl());
        config.setApiKey("");
        LlmClient client = LlmFixtures.client("text", config);
        LlmRequest request = LlmRequest.text("system", "user");

        // when, then
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmNotConfiguredException.class);
        assertThat(LlmWireMockStubs.requestCount(ANTHROPIC_PATH)).isZero();
    }

    @Test
    void complete_whenSystemPromptBlank_omitsItFromRequest(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("odpowiedź");
        LlmClient client = client(wireMock);

        // when
        assertThatCode(() -> client.complete(LlmRequest.text("  ", "user"))).doesNotThrowAnyException();

        // then
        assertThat(LlmWireMockStubs.lastRequestBody(ANTHROPIC_PATH).has("system")).isFalse();
    }

    private LlmClient client(WireMockRuntimeInfo wireMock) {

        LlmClientProperties config = LlmFixtures.config(LlmProvider.ANTHROPIC, wireMock.getHttpBaseUrl());
        return LlmFixtures.client("text", config);
    }

    record Dish(String title, int servings) {

    }
}
