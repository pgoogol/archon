package com.pgoogol.httpexchangelogger.resolver;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeSamplerTest {

    private static HttpExchangeLoggerProperties propertiesWithRate(double rate) {

        HttpExchangeLoggerProperties properties = new HttpExchangeLoggerProperties();
        properties.getSampling().setRate(rate);
        return properties;
    }

    private static HttpExchangeLoggerProperties.Endpoint rule(String pattern, Double sampleRate) {

        HttpExchangeLoggerProperties.Endpoint endpoint = new HttpExchangeLoggerProperties.Endpoint();
        endpoint.setPattern(pattern);
        endpoint.setSampleRate(sampleRate);
        return endpoint;
    }

    private static ExchangeSampler sampler(HttpExchangeLoggerProperties properties, DoubleSupplier randomSource) {

        return new ExchangeSampler(properties, new AntPathMatcher(), randomSource);
    }

    @Test
    void shouldSample_whenRateIsOne_alwaysReturnsTrue() {

        // given
        ExchangeSampler sampler = sampler(propertiesWithRate(1.0d), () -> 0.999d);

        // when
        boolean sampled = sampler.shouldSample("/api/orders");

        // then
        assertThat(sampled).isTrue();
    }

    @Test
    void shouldSample_whenRateIsZero_alwaysReturnsFalse() {

        // given
        ExchangeSampler sampler = sampler(propertiesWithRate(0.0d), () -> 0.0d);

        // when
        boolean sampled = sampler.shouldSample("/api/orders");

        // then
        assertThat(sampled).isFalse();
    }

    @Test
    void shouldSample_whenRandomValueBelowRate_returnsTrue() {

        // given
        ExchangeSampler sampler = sampler(propertiesWithRate(0.5d), () -> 0.3d);

        // when
        boolean sampled = sampler.shouldSample("/api/orders");

        // then
        assertThat(sampled).isTrue();
    }

    @Test
    void shouldSample_whenRandomValueAtOrAboveRate_returnsFalse() {

        // given
        ExchangeSampler sampler = sampler(propertiesWithRate(0.5d), () -> 0.5d);

        // when
        boolean sampled = sampler.shouldSample("/api/orders");

        // then
        assertThat(sampled).isFalse();
    }

    @Test
    void resolveRate_whenNoRuleMatches_usesGlobalRate() {

        // given
        HttpExchangeLoggerProperties properties = propertiesWithRate(0.25d);
        properties.setEndpoints(List.of(rule("/api/orders/**", 0.75d)));
        ExchangeSampler sampler = sampler(properties, () -> 0.0d);

        // when
        double rate = sampler.resolveRate("/api/products");

        // then
        assertThat(rate).isEqualTo(0.25d);
    }

    @Test
    void resolveRate_whenRuleWithSampleRateMatches_usesRuleRate() {

        // given
        HttpExchangeLoggerProperties properties = propertiesWithRate(1.0d);
        properties.setEndpoints(List.of(
                rule("/api/orders/**", 0.1d),
                rule("/api/**", 0.9d)
        ));
        ExchangeSampler sampler = sampler(properties, () -> 0.0d);

        // when
        double rate = sampler.resolveRate("/api/orders/123");

        // then first matching rule wins
        assertThat(rate).isEqualTo(0.1d);
    }

    @Test
    void resolveRate_whenMatchingRuleHasNoSampleRate_fallsBackToGlobalRate() {

        // given
        HttpExchangeLoggerProperties properties = propertiesWithRate(0.4d);
        properties.setEndpoints(List.of(rule("/api/orders/**", null)));
        ExchangeSampler sampler = sampler(properties, () -> 0.0d);

        // when
        double rate = sampler.resolveRate("/api/orders/123");

        // then
        assertThat(rate).isEqualTo(0.4d);
    }

    @Test
    void shouldSample_whenRateAboveOne_isClampedToAlwaysSample() {

        // given
        ExchangeSampler sampler = sampler(propertiesWithRate(5.0d), () -> 0.999d);

        // when
        boolean sampled = sampler.shouldSample("/api/orders");

        // then
        assertThat(sampled).isTrue();
    }

    @Test
    void shouldSample_whenRateNegative_isClampedToNeverSample() {

        // given
        ExchangeSampler sampler = sampler(propertiesWithRate(-1.0d), () -> 0.0d);

        // when
        boolean sampled = sampler.shouldSample("/api/orders");

        // then
        assertThat(sampled).isFalse();
    }

    @Test
    void resolveRate_whenPathIsNull_usesGlobalRate() {

        // given
        HttpExchangeLoggerProperties properties = propertiesWithRate(0.7d);
        properties.setEndpoints(List.of(rule("/api/**", 0.2d)));
        ExchangeSampler sampler = sampler(properties, () -> 0.0d);

        // when
        double rate = sampler.resolveRate(null);

        // then
        assertThat(rate).isEqualTo(0.7d);
    }

}
