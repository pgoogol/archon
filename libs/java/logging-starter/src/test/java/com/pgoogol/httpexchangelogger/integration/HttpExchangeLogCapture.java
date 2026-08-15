package com.pgoogol.httpexchangelogger.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pgoogol.httpexchangelogger.filter.HttpExchangeLoggingFilter;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Captures the JSON lines the filter emits to the {@code http.exchange.logger} SLF4J logger and
 * exposes them as parsed {@link JsonNode} events. Attach in {@code @BeforeEach}, detach in
 * {@code @AfterEach}.
 */
public class HttpExchangeLogCapture {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public void attach() {

        appender.list.clear();
        appender.start();
        exchangeLogger().addAppender(appender);
    }

    public void detach() {

        exchangeLogger().detachAppender(appender);
        appender.stop();
        appender.list.clear();
    }

    public List<JsonNode> getEvents() {

        return appender.list.stream()
                .map(loggingEvent -> objectMapper.readTree(loggingEvent.getFormattedMessage()))
                .toList();
    }

    public JsonNode last() {

        List<JsonNode> events = getEvents();
        if (events.isEmpty()) {

            throw new IllegalStateException("No events captured");
        }
        return events.getLast();
    }

    public void clear() {

        appender.list.clear();
    }

    private Logger exchangeLogger() {

        return (Logger) LoggerFactory.getLogger(HttpExchangeLoggingFilter.LOGGER_NAME);
    }

}
