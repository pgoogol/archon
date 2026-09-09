package com.pgoogol.llm.provider;

import com.pgoogol.llm.ContentPart;
import com.pgoogol.llm.Effort;
import com.pgoogol.llm.ImagePart;
import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.LlmMessage;
import com.pgoogol.llm.LlmRequest;
import com.pgoogol.llm.LlmResponse;
import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.StopReason;
import com.pgoogol.llm.TextPart;
import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.usage.LlmUsagePublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Provider zgodny z API OpenAI ({@code POST /v1/chat/completions}) — pokrywa
 * OpenAI, OpenRouter, Groq, Mistral i serwery lokalne. Obrazy idą jako data-URI,
 * wyjście strukturalne przez {@code response_format} ze {@code strict}.
 *
 * <p>Limit tokenów wysyłamy jako {@code max_completion_tokens}: modele
 * rozumujące odrzucają dawne {@code max_tokens}.
 */
public class OpenAiCompatibleLlmClient extends AbstractHttpLlmClient {

    private static final String PATH = "/v1/chat/completions";

    public OpenAiCompatibleLlmClient(String name,
                                     LlmClientProperties config,
                                     RestClient restClient,
                                     ObjectMapper objectMapper,
                                     LlmUsagePublisher usagePublisher) {

        super(name, config, restClient, objectMapper, usagePublisher);
    }

    @Override
    protected String path() {

        return PATH;
    }

    @Override
    protected ObjectNode buildBody(LlmRequest request, JsonSchema schema, LlmClientProperties config) {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());
        body.put("max_completion_tokens", request.options().maxTokensValue().orElseGet(config::getMaxTokens));
        body.set("messages", messages(request));
        temperature(request, config).ifPresent(value -> body.put("temperature", value));
        effort(request, config).ifPresent(value -> body.put("reasoning_effort", value.wireValue()));
        if (Objects.nonNull(schema)) {

            body.set("response_format", responseFormat(schema));
        }
        return body;
    }

    @Override
    protected LlmResponse parse(JsonNode payload) {

        JsonNode choice = payload.path("choices").path(0);
        Optional<String> text = Optional.of(choice.path("message").path("content").asString(""));
        StopReason stopReason = stopReason(choice.path("finish_reason").asString(""));
        return new LlmResponse(requireText(text), stopReason, usage(payload.path("usage")),
                payload.path("model").asString(""));
    }

    private ArrayNode messages(LlmRequest request) {

        ArrayNode messages = objectMapper.createArrayNode();
        request.systemPrompt().ifPresent(system -> messages.add(systemMessage(system)));
        request.messages().forEach(message -> messages.add(message(message)));
        return messages;
    }

    private ObjectNode systemMessage(String system) {

        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", "system");
        node.put("content", system);
        return node;
    }

    private ObjectNode message(LlmMessage message) {

        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", message.role().wireValue());
        ArrayNode content = objectMapper.createArrayNode();
        message.parts().forEach(part -> content.add(part(part)));
        node.set("content", content);
        return node;
    }

    private ObjectNode part(ContentPart part) {

        if (part instanceof TextPart text) {

            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "text");
            node.put("text", text.text());
            return node;
        }
        ImagePart image = (ImagePart) part;
        return imageNode(image);
    }

    private ObjectNode imageNode(ImagePart image) {

        ObjectNode url = objectMapper.createObjectNode();
        url.put("url", image.dataUri());
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "image_url");
        node.set("image_url", url);
        return node;
    }

    private ObjectNode responseFormat(JsonSchema schema) {

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", schema.name());
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", objectMapper.readTree(schema.json()));
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.set("json_schema", jsonSchema);
        return format;
    }

    /**
     * Wysyłamy tylko wtedy, gdy ktoś ustawił wartość — modele rozumujące
     * odrzucają ten parametr błędem 400.
     */
    private Optional<Double> temperature(LlmRequest request, LlmClientProperties config) {

        Optional<Double> fromRequest = request.options().temperatureValue();
        if (fromRequest.isPresent()) {

            return fromRequest;
        }
        return Optional.ofNullable(config.getTemperature());
    }

    private Optional<Effort> effort(LlmRequest request, LlmClientProperties config) {

        Optional<Effort> fromRequest = request.options().effortValue();
        if (fromRequest.isPresent()) {

            return fromRequest;
        }
        return Optional.ofNullable(config.getEffort());
    }

    private StopReason stopReason(String raw) {

        return switch (raw) {

            case "stop", "" -> StopReason.COMPLETED;
            case "length" -> StopReason.MAX_TOKENS;
            case "content_filter" -> StopReason.FILTERED;
            default -> StopReason.OTHER;
        };
    }

    private LlmUsage usage(JsonNode usage) {

        long cached = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
        long prompt = usage.path("prompt_tokens").asLong(0);
        return new LlmUsage(Math.max(prompt - cached, 0), usage.path("completion_tokens").asLong(0), cached, 0);
    }
}
