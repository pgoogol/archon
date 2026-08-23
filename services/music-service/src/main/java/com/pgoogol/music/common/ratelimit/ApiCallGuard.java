package com.pgoogol.music.common.ratelimit;

import com.pgoogol.music.common.ExternalServiceException;
import com.pgoogol.music.common.RateLimitedException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wspólna ochrona wywołań zewnętrznych API (M1.3): rate limiter + retry
 * z wykładniczym backoffem (Resilience4j). Ponawiane są wyłącznie
 * {@link ExternalServiceException} (5xx/timeout/429); 4xx nie są ponawiane.
 * Przy 429 z nagłówkiem Retry-After odczekiwany jest wskazany czas — ale tylko
 * do {@link #MAX_HONORED_RETRY_AFTER}; dłuższy leci do wywołującego.
 * Limiter działa wewnątrz retry — każda ponowiona próba też podlega limitowi.
 */
public final class ApiCallGuard {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration PERMIT_WAIT_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Powyżej tego progu {@code Retry-After} nie jest odczekiwany, tylko zgłoszony
     * wywołującemu. Wyczerpanie kwoty dobowej potrafi wrócić z „ponów za 22
     * godziny" — odczekanie tego w miejscu oznaczałoby wątek stojący do jutra
     * i żądanie HTTP, które nigdy nie odpowiada.
     */
    public static final Duration MAX_HONORED_RETRY_AFTER = Duration.ofSeconds(30);

    private final RateLimiter rateLimiter;
    private final Retry retry;

    private ApiCallGuard(RateLimiter rateLimiter, Retry retry) {

        this.rateLimiter = rateLimiter;
        this.retry = retry;
    }

    public static ApiCallGuard of(String name, int requestsPerSecond) {

        return of(name, requestsPerSecond, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF);
    }

    public static ApiCallGuard of(String name, int requestsPerSecond, int maxAttempts,
                                  Duration initialBackoff) {

        Objects.requireNonNull(name, "name");
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(requestsPerSecond)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(PERMIT_WAIT_TIMEOUT)
            .build();
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .retryOnException(ApiCallGuard::isRetriable)
            .intervalBiFunction((attempt, either) -> backoffMillis(attempt, initialBackoff,
                either.isLeft() ? either.getLeft() : null))
            .build();
        return new ApiCallGuard(
            RateLimiter.of(name, rateLimiterConfig),
            Retry.of(name, retryConfig));
    }

    public <T> T execute(Supplier<T> call) {

        Objects.requireNonNull(call, "call");
        return Retry.decorateSupplier(retry, RateLimiter.decorateSupplier(rateLimiter, call)).get();
    }

    /**
     * Ponawiamy 5xx, timeouty i te 429, których {@code Retry-After} mieści się
     * w {@link #MAX_HONORED_RETRY_AFTER}. Dłuższa przerwa to nie chwilowe
     * przeciążenie, tylko wyczerpany budżet — z takim odczekiwaniem nie ma co
     * czekać w miejscu, decyzję podejmuje wywołujący.
     */
    private static boolean isRetriable(Throwable failure) {

        if (failure instanceof RateLimitedException rateLimited
                && Objects.nonNull(rateLimited.getRetryAfter())) {

            return rateLimited.getRetryAfter().compareTo(MAX_HONORED_RETRY_AFTER) <= 0;
        }
        return failure instanceof ExternalServiceException;
    }

    private static long backoffMillis(int attempt, Duration initialBackoff, Throwable failure) {

        if (failure instanceof RateLimitedException rateLimited
                && Objects.nonNull(rateLimited.getRetryAfter())) {

            return rateLimited.getRetryAfter().toMillis();
        }
        return initialBackoff.toMillis() * (1L << (attempt - 1));
    }
}
