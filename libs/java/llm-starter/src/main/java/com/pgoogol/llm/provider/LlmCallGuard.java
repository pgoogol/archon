package com.pgoogol.llm.provider;

import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.exception.LlmRateLimitedException;
import com.pgoogol.llm.exception.LlmUnavailableException;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Limiter i ponowienia wokół wywołania providera. Limiter działa wewnątrz
 * ponowienia — każda kolejna próba też podlega limitowi, inaczej seria ponowień
 * przebijałaby próg, który miała chronić.
 *
 * <p>Ponawiamy 5xx, timeouty i te 429, których {@code Retry-After} mieści się
 * w progu z konfiguracji. Odrzucone żądanie (4xx) nie jest ponawiane — ten sam
 * zły klucz albo za długi kontekst wrócą tym samym błędem i tym samym rachunkiem.
 */
public class LlmCallGuard {

    private static final Duration PERMIT_WAIT_TIMEOUT = Duration.ofMinutes(5);

    private final RateLimiter rateLimiter;
    private final Retry retry;
    private final Duration maxHonoredRetryAfter;
    private final Duration initialBackoff;

    public LlmCallGuard(String name, LlmClientProperties config) {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(config, "config");
        this.maxHonoredRetryAfter = config.getMaxHonoredRetryAfter();
        this.initialBackoff = config.getInitialBackoff();
        this.rateLimiter = RateLimiter.of(name, rateLimiterConfig(config));
        this.retry = Retry.of(name, retryConfig(config));
    }

    public <T> T execute(Supplier<T> call) {

        Objects.requireNonNull(call, "call");
        Supplier<T> limited = RateLimiter.decorateSupplier(rateLimiter, call);
        return Retry.decorateSupplier(retry, limited).get();
    }

    private RetryConfig retryConfig(LlmClientProperties config) {

        return RetryConfig.custom()
                .maxAttempts(config.getMaxAttempts())
                .retryOnException(this::isRetriable)
                .intervalBiFunction(this::backoffMillis)
                .build();
    }

    private boolean isRetriable(Throwable failure) {

        if (failure instanceof LlmRateLimitedException rateLimited) {

            Optional<Duration> retryAfter = rateLimited.getRetryAfter();
            return retryAfter.map(this::withinHonoredWindow).orElse(Boolean.TRUE);
        }
        return failure instanceof LlmUnavailableException;
    }

    /**
     * Dłuższa przerwa niż próg to nie chwilowe przeciążenie, tylko wyczerpany
     * budżet — decyzję, co z tym zrobić, podejmuje wywołujący.
     */
    private boolean withinHonoredWindow(Duration retryAfter) {

        return retryAfter.compareTo(maxHonoredRetryAfter) <= 0;
    }

    /**
     * Przerwa przed kolejną próbą: {@code Retry-After} providera, gdy go podał,
     * w przeciwnym razie backoff wykładniczy od wartości z konfiguracji.
     */
    private Long backoffMillis(Integer attempt, Either<Throwable, Object> outcome) {

        if (outcome.isLeft() && outcome.getLeft() instanceof LlmRateLimitedException rateLimited) {

            Optional<Duration> retryAfter = rateLimited.getRetryAfter();
            return retryAfter.map(Duration::toMillis).orElseGet(() -> exponential(attempt));
        }
        return exponential(attempt);
    }

    private long exponential(int attempt) {

        return initialBackoff.toMillis() * (1L << (attempt - 1));
    }

    private static RateLimiterConfig rateLimiterConfig(LlmClientProperties config) {

        return RateLimiterConfig.custom()
                .limitForPeriod(config.getRequestsPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(PERMIT_WAIT_TIMEOUT)
                .build();
    }
}
