package com.pgoogol.llm.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * Provider odesłał 429. {@code retryAfter} pochodzi z nagłówka {@code Retry-After}
 * i bywa pusty — wtedy o przerwie decyduje backoff.
 */
public class LlmRateLimitedException extends LlmException {

    private final transient Duration retryAfter;

    public LlmRateLimitedException(String message, Duration retryAfter, Throwable cause) {

        super(LlmErrorCodes.RATE_LIMITED, message, cause);
        this.retryAfter = retryAfter;
    }

    public LlmRateLimitedException(String message, Duration retryAfter) {

        super(LlmErrorCodes.RATE_LIMITED, message);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> getRetryAfter() {

        return Optional.ofNullable(retryAfter);
    }
}
