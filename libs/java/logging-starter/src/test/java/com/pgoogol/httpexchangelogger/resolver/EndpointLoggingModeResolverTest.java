package com.pgoogol.httpexchangelogger.resolver;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointLoggingModeResolverTest {

    private EndpointLoggingModeResolver resolverWithRules(HttpLogMode defaultMode,
                                                          List<HttpExchangeLoggerProperties.Endpoint> endpoints) {

        HttpExchangeLoggerProperties properties = new HttpExchangeLoggerProperties();
        properties.setDefaultMode(defaultMode);
        properties.setEndpoints(endpoints);
        return new EndpointLoggingModeResolver(properties);
    }

    private HttpExchangeLoggerProperties.Endpoint rule(String pattern, HttpLogMode mode) {

        HttpExchangeLoggerProperties.Endpoint endpoint = new HttpExchangeLoggerProperties.Endpoint();
        endpoint.setPattern(pattern);
        endpoint.setMode(mode);
        return endpoint;
    }

    @Test
    void resolve_whenNoRulesMatch_returnsDefaultMode() {

        // given
        EndpointLoggingModeResolver resolver = resolverWithRules(HttpLogMode.BASIC, new ArrayList<>());

        // when
        HttpLogMode mode = resolver.resolve("/api/anything");

        // then
        assertThat(mode).isEqualTo(HttpLogMode.BASIC);
    }

    @Test
    void resolve_whenMultipleRulesMatch_returnsFirstMatchingMode() {

        // given
        EndpointLoggingModeResolver resolver = resolverWithRules(HttpLogMode.BASIC, List.of(
                rule("/api/orders/**", HttpLogMode.FULL),
                rule("/api/**", HttpLogMode.LIMITED)
        ));

        // when / then
        assertThat(resolver.resolve("/api/orders/123")).isEqualTo(HttpLogMode.FULL);
        assertThat(resolver.resolve("/api/products")).isEqualTo(HttpLogMode.LIMITED);
    }

    @Test
    void resolve_whenOffRuleMatches_returnsOffMode() {

        // given
        EndpointLoggingModeResolver resolver = resolverWithRules(HttpLogMode.FULL, List.of(
                rule("/actuator/**", HttpLogMode.OFF)
        ));

        // when / then
        assertThat(resolver.resolve("/actuator/health")).isEqualTo(HttpLogMode.OFF);
        assertThat(resolver.resolve("/api/orders")).isEqualTo(HttpLogMode.FULL);
    }

    @Test
    void resolve_whenRulesMissingPatternOrMode_ignoresThemAndUsesValidRule() {

        // given
        EndpointLoggingModeResolver resolver = resolverWithRules(HttpLogMode.BASIC, List.of(
                rule(null, HttpLogMode.FULL),
                rule("/api/**", null),
                rule("/api/orders/**", HttpLogMode.FULL)
        ));

        // when
        HttpLogMode mode = resolver.resolve("/api/orders/9");

        // then
        assertThat(mode).isEqualTo(HttpLogMode.FULL);
    }

}
