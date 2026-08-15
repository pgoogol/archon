package com.pgoogol.httpexchangelogger.runtime;

import com.pgoogol.httpexchangelogger.model.HttpLogMode;

import java.time.Instant;
import java.util.Objects;

/**
 * A temporary, runtime-scoped logging mode override. A {@code null} pattern
 * means the override applies globally; a {@code null} expiry means it stays
 * active until cleared.
 */
public record ModeOverride(String pattern, HttpLogMode mode, Instant expiresAt) {

    public boolean isGlobal() {

        return Objects.isNull(pattern);
    }

    public boolean isExpired(Instant now) {

        return Objects.nonNull(expiresAt) && !expiresAt.isAfter(now);
    }

}
