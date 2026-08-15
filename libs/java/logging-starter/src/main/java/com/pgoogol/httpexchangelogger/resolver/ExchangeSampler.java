package com.pgoogol.httpexchangelogger.resolver;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Decides whether a given exchange should be logged, based on the configured
 * sampling rate. A rate of {@code 1.0} logs every exchange, {@code 0.0} logs
 * nothing. Endpoint rules may override the global rate with {@code sample-rate}
 * (first matching rule that defines one wins).
 */
public class ExchangeSampler {

    private final HttpExchangeLoggerProperties properties;
    private final PathMatcher pathMatcher;
    private final DoubleSupplier randomSource;

    public ExchangeSampler(HttpExchangeLoggerProperties properties) {

        this(properties, new AntPathMatcher(), () -> ThreadLocalRandom.current().nextDouble());
    }

    ExchangeSampler(HttpExchangeLoggerProperties properties, PathMatcher pathMatcher, DoubleSupplier randomSource) {

        this.properties = properties;
        this.pathMatcher = pathMatcher;
        this.randomSource = randomSource;
    }

    public boolean shouldSample(String path) {

        double rate = clamp(resolveRate(path));
        if (rate >= 1.0d) {

            return true;
        }
        if (rate <= 0.0d) {

            return false;
        }
        return randomSource.getAsDouble() < rate;
    }

    public double resolveRate(String path) {

        if (Objects.isNull(path)) {

            return properties.getSampling().getRate();
        }

        return properties.getEndpoints().stream()
                .filter(rule -> Objects.nonNull(rule.getPattern()) && Objects.nonNull(rule.getSampleRate()))
                .filter(rule -> pathMatcher.match(rule.getPattern(), path))
                .map(HttpExchangeLoggerProperties.Endpoint::getSampleRate)
                .findFirst()
                .orElseGet(() -> properties.getSampling().getRate());
    }

    private double clamp(double rate) {

        return Math.min(1.0d, Math.max(0.0d, rate));
    }

}
