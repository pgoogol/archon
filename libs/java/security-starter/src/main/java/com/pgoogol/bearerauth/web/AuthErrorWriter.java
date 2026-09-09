package com.pgoogol.bearerauth.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pisze odpowiedź błędu w formacie, którego używa cała reszta API
 * (errorCode, message, timestamp, traceId).
 *
 * <p>Starter nie może sięgnąć po klasę {@code ErrorResponse} z serwisu —
 * zależność szłaby w złą stronę i przewróciłaby test architektury — więc składa
 * ten sam kształt sam. Front rozpoznaje wyłącznie kształt, nie klasę.</p>
 */
public class AuthErrorWriter {

    private static final String TRACE_ID_KEY = "traceId";

    private final ObjectMapper objectMapper;

    public AuthErrorWriter(ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = objectMapper.writeValueAsString(payload(errorCode, message));
        response.getWriter().write(body);
    }

    private Map<String, Object> payload(String errorCode, String message) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        payload.put("timestamp", Instant.now().toString());
        payload.put("traceId", traceId());
        return payload;
    }

    private String traceId() {

        String traceId = MDC.get(TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {

            return "";
        }
        return traceId;
    }
}
