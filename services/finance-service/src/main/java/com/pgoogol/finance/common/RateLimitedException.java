package com.pgoogol.finance.common;

import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * Dostawca odrzucił wywołanie limitem (429). Gdy podał {@code Retry-After},
 * ponowienie czeka wskazany czas zamiast zwykłego backoffu.
 */
public class RateLimitedException extends ExternalServiceException {

    private final transient Duration retryAfter;

    public RateLimitedException(String errorCode, String message, @Nullable Duration retryAfter) {

        super(errorCode, message);
        this.retryAfter = retryAfter;
    }

    @Nullable
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
