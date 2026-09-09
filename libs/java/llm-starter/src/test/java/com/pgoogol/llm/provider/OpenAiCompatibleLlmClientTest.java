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
import com.pgoogol.llm.exception.LlmResponseException;
import com.pgoogol.llm.support.LlmFixtures;
import com.pgoogol.llm.support.LlmWireMockStubs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.pgoogol.llm.support.LlmWireMockStubs.OPENAI_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class OpenAiCompatibleLlmClientTest {

    private static final byte[] IMAGE = "udawany-png".getBytes(StandardCharsets.UTF_8);

    @Test
    void complete_whenProviderResponds_returnsTextAndUsageWithCacheSplitOut(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("Naleśniki");
        LlmClient client = client(wireMock);

        // when
        LlmResponse response = client.complete(LlmRequest.text("system", "user"));

        // then: tokeny z cache wychodzą z prompt_tokens, żeby nie liczyć ich dwa razy
        assertThat(response.text()).isEqualTo("Naleśniki");
        assertThat(response.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(response.usage()).isEqualTo(new LlmUsage(100, 30, 20, 0));
        verify(postRequestedFor(urlPathEqualTo(OPENAI_PATH))
                .withHeader("Authorization", equalTo("Bearer " + LlmFixtures.API_KEY)));
    }

    @Test
    void complete_whenSystemPromptGiven_sendsItAsFirstMessage(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("odpowiedź");
        LlmClient client = client(wireMock);

        // when
        client.complete(LlmRequest.text("jesteś parserem", "user"));

        // then
        JsonNode messages = LlmWireMockStubs.lastRequestBody(OPENAI_PATH).path("messages");
        assertThat(messages.path(0).path("role").asString()).isEqualTo("system");
        assertThat(messages.path(0).path("content").asString()).isEqualTo("jesteś parserem");
        assertThat(messages.path(1).path("role").asString()).isEqualTo("user");
    }

    @Test
    void complete_whenTemperatureNotSet_omitsItAndUsesMaxCompletionTokens(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("odpowiedź");
        LlmClient client = client(wireMock);

        // when
        client.complete(LlmRequest.text("system", "user"));

        // then
        JsonNode body = LlmWireMockStubs.lastRequestBody(OPENAI_PATH);
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(body.has("max_tokens")).isFalse();
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void complete_whenEffortSetInConfiguration_sendsReasoningEffort(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("odpowiedź");
        LlmClientProperties config = LlmFixtures.config(LlmProvider.OPENAI, wireMock.getHttpBaseUrl());
        config.setEffort(Effort.LOW);
        LlmClient client = LlmFixtures.client("vision", config);

        // when
        client.complete(LlmRequest.text("system", "user"));

        // then
        assertThat(LlmWireMockStubs.lastRequestBody(OPENAI_PATH).path("reasoning_effort").asString())
                .isEqualTo("low");
    }

    @Test
    void complete_whenRequestOverridesConfiguration_requestWins(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("odpowiedź");
        LlmClientProperties config = LlmFixtures.config(LlmProvider.OPENAI, wireMock.getHttpBaseUrl());
        config.setEffort(Effort.LOW);
        config.setTemperature(0.1);
        LlmClient client = LlmFixtures.client("vision", config);
        LlmRequest request = LlmRequest.text("system", "user")
                .withOptions(new LlmOptions(64, Effort.HIGH, 0.9));

        // when
        client.complete(request);

        // then
        JsonNode body = LlmWireMockStubs.lastRequestBody(OPENAI_PATH);
        assertThat(body.path("reasoning_effort").asString()).isEqualTo("high");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.9);
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(64);
    }

    @Test
    void complete_whenRequestCarriesImage_sendsDataUri(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("odpowiedź");
        LlmClient client = client(wireMock);
        LlmRequest request = LlmRequest.of("system", new TextPart("co to?"), new ImagePart(IMAGE, "image/png"));

        // when
        client.complete(request);

        // then
        JsonNode image = LlmWireMockStubs.lastRequestBody(OPENAI_PATH)
                .path("messages").path(1).path("content").path(1);
        assertThat(image.path("type").asString()).isEqualTo("image_url");
        assertThat(image.path("image_url").path("url").asString()).startsWith("data:image/png;base64,");
    }

    @Test
    void extract_whenSchemaGiven_sendsStrictResponseFormatAndParsesRecord(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("{\"title\":\"Naleśniki\",\"servings\":4}");
        LlmClient client = client(wireMock);
        JsonSchema schema = new JsonSchema("recipe", "{\"type\":\"object\"}");

        // when
        LlmExtraction<Dish> extraction = client.extract(LlmRequest.text("system", "user"), schema, Dish.class);

        // then
        assertThat(extraction.value()).isEqualTo(new Dish("Naleśniki", 4));
        JsonNode format = LlmWireMockStubs.lastRequestBody(OPENAI_PATH).path("response_format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("name").asString()).isEqualTo("recipe");
    }

    @Test
    void extract_whenResponseIsNotTheExpectedShape_reportsUnreadableResponse(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("to nie jest JSON");
        LlmClient client = client(wireMock);
        JsonSchema schema = new JsonSchema("recipe", "{\"type\":\"object\"}");
        LlmRequest request = LlmRequest.text("system", "user");

        // when, then
        assertThatThrownBy(() -> client.extract(request, schema, Dish.class))
                .isInstanceOf(LlmResponseException.class)
                .hasFieldOrPropertyWithValue("errorCode", LlmErrorCodes.RESPONSE_INVALID);
    }

    @Test
    void complete_whenProviderReturnsEmptyContent_reportsEmptyResponse(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.openAiAnswers("");
        LlmClient client = client(wireMock);
        LlmRequest request = LlmRequest.text("system", "user");

        // when, then
        assertThatThrownBy(() -> client.complete(request))
                .isInstanceOf(LlmResponseException.class)
                .hasFieldOrPropertyWithValue("errorCode", LlmErrorCodes.RESPONSE_EMPTY);
    }

    private LlmClient client(WireMockRuntimeInfo wireMock) {

        LlmClientProperties config = LlmFixtures.config(LlmProvider.OPENAI, wireMock.getHttpBaseUrl());
        return LlmFixtures.client("vision", config);
    }

    record Dish(String title, int servings) {

    }
}
