package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.support.ContentTypeMatcher;
import org.springframework.util.CollectionUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public class JsonBodySanitizer implements BodySanitizer {

    public static final String UNPARSEABLE_PLACEHOLDER = "[not logged: body could not be parsed for masking]";

    private final ObjectMapper objectMapper;
    private final SensitiveValueMasker masker;
    private final boolean maskingEnabled;
    private final boolean hasSensitiveFields;

    public JsonBodySanitizer(ObjectMapper objectMapper, SensitiveValueMasker masker, HttpExchangeLoggerProperties.Mask maskProperties) {

        this.objectMapper = objectMapper;
        this.masker = masker;
        this.maskingEnabled = Objects.nonNull(maskProperties) && maskProperties.isEnabled();
        this.hasSensitiveFields = Objects.nonNull(maskProperties) && //
                Objects.nonNull(maskProperties.getFields()) && //
                !CollectionUtils.isEmpty(maskProperties.getFields());
    }

    @Override
    public String sanitize(String body, String contentType) {

        if (Objects.isNull(body) || body.isEmpty()) {

            return body;
        }
        if (!maskingEnabled) {

            return body;
        }
        if (!ContentTypeMatcher.isJson(contentType)) {

            return body;
        }

        try {

            JsonNode root = objectMapper.readTree(body);
            JsonNode sanitized = mask(root);
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {

            // Fail closed: masking was requested but the body could not be parsed
            // (malformed or truncated JSON). Never echo the raw body — it may carry secrets.
            if (hasSensitiveFields) {

                return UNPARSEABLE_PLACEHOLDER;
            }
            return body;
        }
    }

    private JsonNode mask(JsonNode node) {

        if (Objects.isNull(node) || node.isNull()) {

            return node;
        }
        if (node.isObject()) {

            ObjectNode object = (ObjectNode) node;
            Set<Map.Entry<String, JsonNode>> properties = object.properties();
            properties.stream()
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(name -> maskProperty(object, name));
            return object;
        }
        if (node.isArray()) {

            ArrayNode array = (ArrayNode) node;
            IntStream.range(0, array.size()) //
                    .mapToObj(array::get) //
                    .filter(item -> item.isObject() || item.isArray()) //
                    .forEach(this::mask);
            return array;
        }
        return node;
    }

    private void maskProperty(ObjectNode object, String name) {

        if (masker.isSensitive(name)) {

            object.put(name, SensitiveValueMasker.MASK);
            return;
        }
        JsonNode value = object.get(name);
        if (Objects.nonNull(value) && (value.isObject() || value.isArray())) {

            mask(value);
        }
    }

}
