package com.pgoogol.httpexchangelogger.serialization;

import com.pgoogol.httpexchangelogger.model.HttpExchangeLogEvent;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class HttpExchangeLogEventJsonWriter {

    private static final String STATIC_FALLBACK =
            "{\"type\":\"http_exchange\",\"serializationError\":\"unserializable event\"}";

    private final ObjectMapper objectMapper;

    public HttpExchangeLogEventJsonWriter(ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
    }

    public String write(HttpExchangeLogEvent event) {

        try {

            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {

            return fallback(ex);
        }
    }

    private String fallback(Exception ex) {

        try {

            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "http_exchange");
            node.put("serializationError", String.valueOf(ex.getMessage()));
            return objectMapper.writeValueAsString(node);
        } catch (Exception inner) {

            return STATIC_FALLBACK;
        }
    }

}
