package com.pgoogol.llm.support;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Stuby obu providerów. Odpowiedzi budowane Jacksonem, nie sklejane z napisów:
 * treść odpowiedzi bywa JSON-em w JSON-ie, a ręczne uciekanie cudzysłowów w
 * teście kończy się testem, który bada uciekanie, a nie klienta.
 */
public final class LlmWireMockStubs {

    public static final String ANTHROPIC_PATH = "/v1/messages";
    public static final String OPENAI_PATH = "/v1/chat/completions";

    private static final ObjectMapper MAPPER = LlmFixtures.objectMapper();

    private LlmWireMockStubs() {

    }

    public static void anthropicAnswers(String text) {

        anthropicAnswers(text, "end_turn");
    }

    public static void anthropicTruncated(String partialText) {

        anthropicAnswers(partialText, "max_tokens");
    }

    public static void anthropicAnswers(String text, String stopReason) {

        stubFor(post(urlPathEqualTo(ANTHROPIC_PATH)).willReturn(okJson(anthropicBody(text, stopReason))));
    }

    public static void openAiAnswers(String text) {

        openAiAnswers(text, "stop");
    }

    public static void openAiTruncated(String partialText) {

        openAiAnswers(partialText, "length");
    }

    public static void openAiAnswers(String text, String finishReason) {

        stubFor(post(urlPathEqualTo(OPENAI_PATH)).willReturn(okJson(openAiBody(text, finishReason))));
    }

    public static void rateLimited(String path, int retryAfterSeconds) {

        stubFor(post(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Retry-After", String.valueOf(retryAfterSeconds))
                .withBody("{\"error\":\"rate limited\"}")));
    }

    /** Pierwsze wywołanie dostaje 429, kolejne odpowiedź — do testu ponowień. */
    public static void rateLimitedThenAnthropicAnswers(String text, int retryAfterSeconds) {

        String scenario = "429-then-ok";
        stubFor(post(urlPathEqualTo(ANTHROPIC_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", String.valueOf(retryAfterSeconds))
                        .withBody("{\"error\":\"rate limited\"}"))
                .willSetStateTo("po-limicie"));
        stubFor(post(urlPathEqualTo(ANTHROPIC_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs("po-limicie")
                .willReturn(okJson(anthropicBody(text, "end_turn"))));
    }

    public static void serverError(String path) {

        stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(503).withBody("przeciążony")));
    }

    public static void badRequest(String path, String body) {

        stubFor(post(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    /** Ciało ostatniego żądania, żeby test mógł sprawdzić, co poszło do modelu. */
    public static JsonNode lastRequestBody(String path) {

        List<LoggedRequest> requests = WireMock.findAll(postRequestedFor(urlPathEqualTo(path)));
        LoggedRequest last = requests.get(requests.size() - 1);
        return MAPPER.readTree(last.getBodyAsString());
    }

    public static int requestCount(String path) {

        return WireMock.findAll(postRequestedFor(urlPathEqualTo(path))).size();
    }

    private static String anthropicBody(String text, String stopReason) {

        ObjectNode block = MAPPER.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        ArrayNode content = MAPPER.createArrayNode();
        content.add(block);
        ObjectNode usage = MAPPER.createObjectNode();
        usage.put("input_tokens", 120);
        usage.put("output_tokens", 30);
        usage.put("cache_read_input_tokens", 10);
        usage.put("cache_creation_input_tokens", 5);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", "test-model-20260101");
        body.put("stop_reason", stopReason);
        body.set("content", content);
        body.set("usage", usage);
        return body.toString();
    }

    private static String openAiBody(String text, String finishReason) {

        ObjectNode message = MAPPER.createObjectNode();
        message.put("role", "assistant");
        message.put("content", text);
        ObjectNode choice = MAPPER.createObjectNode();
        choice.put("finish_reason", finishReason);
        choice.set("message", message);
        ArrayNode choices = MAPPER.createArrayNode();
        choices.add(choice);
        ObjectNode details = MAPPER.createObjectNode();
        details.put("cached_tokens", 20);
        ObjectNode usage = MAPPER.createObjectNode();
        usage.put("prompt_tokens", 120);
        usage.put("completion_tokens", 30);
        usage.set("prompt_tokens_details", details);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", "test-model-20260101");
        body.set("choices", choices);
        body.set("usage", usage);
        return body.toString();
    }
}
