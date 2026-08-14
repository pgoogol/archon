package com.pgoogol.httpexchangelogger.resolver;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.Objects;

public class EndpointLoggingModeResolver {

    private final HttpExchangeLoggerProperties properties;
    private final PathMatcher pathMatcher;

    public EndpointLoggingModeResolver(HttpExchangeLoggerProperties properties) {

        this(properties, new AntPathMatcher());
    }

    public EndpointLoggingModeResolver(HttpExchangeLoggerProperties properties, PathMatcher pathMatcher) {

        this.properties = properties;
        this.pathMatcher = pathMatcher;
    }

    public HttpLogMode resolve(HttpServletRequest request) {

        return resolve(request.getRequestURI());
    }

    public HttpLogMode resolve(String path) {

        if (Objects.isNull(path)) {

            return properties.getDefaultMode();
        }

        return properties.getEndpoints().stream()
                .filter(rule -> Objects.nonNull(rule.getPattern()) && Objects.nonNull(rule.getMode()))
                .filter(rule -> pathMatcher.match(rule.getPattern(), path))
                .map(HttpExchangeLoggerProperties.Endpoint::getMode)
                .findFirst()
                .orElseGet(properties::getDefaultMode);
    }

}
