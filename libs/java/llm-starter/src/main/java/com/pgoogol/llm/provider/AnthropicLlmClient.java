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
 * Provider Anthropic ({@code POST /v1/messages}). Obrazy idą jako base64 w polu
 * {@code source}, wyjście strukturalne przez {@code output_config.format}.
 */
public class AnthropicLlmClient extends AbstractHttpLlmClient {

    public static final String API_VERSION = "2023-06-01";

    private static final String PATH = "/v1/messages";

    public AnthropicLlmClient(String name,
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
        body.put("max_tokens", request.options().maxTokensValue().orElseGet(config::getMaxTokens));
        request.systemPrompt().ifPresent(system -> body.put("system", system));
        body.set("messages", messages(request));
        temperature(request, config).ifPresent(value -> body.put("temperature", value));
        outputConfig(request, schema, config).ifPresent(output -> body.set("output_config", output));
        return body;
    }

    @Override
    protected LlmResponse parse(JsonNode payload) {

        Optional<String> text = textBlock(payload.path("content"));
        StopReason stopReason = stopReason(payload.path("stop_reason").asString(""));
        return new LlmResponse(requireText(text), stopReason, usage(payload.path("usage")),
                payload.path("model").asString(""));
    }

    private ArrayNode messages(LlmRequest request) {

        ArrayNode messages = objectMapper.createArrayNode();
        request.messages().forEach(message -> messages.add(message(message)));
        return messages;
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

        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "base64");
        source.put("media_type", image.mediaType());
        source.put("data", image.base64());
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "image");
        node.set("source", source);
        return node;
    }

    /**
     * Wysyłamy tylko wtedy, gdy ktoś ustawił wartość — nowsze modele odrzucają
     * ten parametr błędem 400, więc domyślna wartość byłaby awarią, nie ustawieniem.
     */
    private Optional<Double> temperature(LlmRequest request, LlmClientProperties config) {

        Optional<Double> fromRequest = request.options().temperatureValue();
        if (fromRequest.isPresent()) {

            return fromRequest;
        }
        return Optional.ofNullable(config.getTemperature());
    }

    private Optional<ObjectNode> outputConfig(LlmRequest request, JsonSchema schema, LlmClientProperties config) {

        Optional<Effort> effort = effort(request, config);
        if (Objects.isNull(schema) && effort.isEmpty()) {

            return Optional.empty();
        }
        ObjectNode output = objectMapper.createObjectNode();
        effort.ifPresent(value -> output.put("effort", value.wireValue()));
        if (Objects.nonNull(schema)) {

            output.set("format", format(schema));
        }
        return Optional.of(output);
    }

    private ObjectNode format(JsonSchema schema) {

        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", schema.name());
        format.set("schema", objectMapper.readTree(schema.json()));
        return format;
    }

    private Optional<Effort> effort(LlmRequest request, LlmClientProperties config) {

        Optional<Effort> fromRequest = request.options().effortValue();
        if (fromRequest.isPresent()) {

            return fromRequest;
        }
        return Optional.ofNullable(config.getEffort());
    }

    private Optional<String> textBlock(JsonNode content) {

        return content.valueStream()
                .filter(block -> Objects.equals(block.path("type").asString(""), "text"))
                .map(block -> block.path("text").asString(""))
                .findFirst();
    }

    private StopReason stopReason(String raw) {

        return switch (raw) {

            case "end_turn", "stop_sequence" -> StopReason.COMPLETED;
            case "max_tokens" -> StopReason.MAX_TOKENS;
            case "refusal" -> StopReason.REFUSAL;
            case "" -> StopReason.COMPLETED;
            default -> StopReason.OTHER;
        };
    }

    private LlmUsage usage(JsonNode usage) {

        return new LlmUsage(
                usage.path("input_tokens").asLong(0),
                usage.path("output_tokens").asLong(0),
                usage.path("cache_read_input_tokens").asLong(0),
                usage.path("cache_creation_input_tokens").asLong(0));
    }
}
