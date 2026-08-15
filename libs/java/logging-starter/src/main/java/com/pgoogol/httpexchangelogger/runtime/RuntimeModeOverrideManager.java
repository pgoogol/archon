package com.pgoogol.httpexchangelogger.runtime;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory store of temporary logging mode overrides, applied at runtime on
 * top of the static configuration (e.g. via the actuator admin endpoint).
 *
 * <p>Overrides may carry a TTL after which they silently expire; expiry is
 * evaluated lazily on access, so no background scheduler is needed. When
 * {@code require-ttl-for-full-logging=true}, a {@code FULL} override without
 * a TTL is rejected.
 */
public class RuntimeModeOverrideManager {

    private final HttpExchangeLoggerProperties properties;
    private final Clock clock;
    private final PathMatcher pathMatcher;
    private final AtomicReference<ModeOverride> globalOverride = new AtomicReference<>();
    private final List<ModeOverride> patternOverrides = new CopyOnWriteArrayList<>();

    public RuntimeModeOverrideManager(HttpExchangeLoggerProperties properties) {

        this(properties, Clock.systemUTC(), new AntPathMatcher());
    }

    RuntimeModeOverrideManager(HttpExchangeLoggerProperties properties, Clock clock, PathMatcher pathMatcher) {

        this.properties = properties;
        this.clock = clock;
        this.pathMatcher = pathMatcher;
    }

    public ModeOverride setGlobalOverride(HttpLogMode mode, Duration ttl) {

        ModeOverride override = newOverride(null, mode, ttl);
        globalOverride.set(override);
        return override;
    }

    public ModeOverride setPatternOverride(String pattern, HttpLogMode mode, Duration ttl) {

        if (!StringUtils.hasText(pattern)) {

            throw new IllegalArgumentException("pattern must not be blank");
        }
        ModeOverride override = newOverride(pattern, mode, ttl);
        patternOverrides.removeIf(existing -> Objects.equals(existing.pattern(), pattern));
        patternOverrides.add(override);
        return override;
    }

    /**
     * Resolves the override mode for the given request path. Pattern overrides
     * win over the global one; among pattern overrides the first match wins
     * (insertion order).
     */
    public Optional<HttpLogMode> resolve(String path) {

        purgeExpired();
        if (Objects.isNull(path)) {

            return globalMode();
        }
        return patternOverrides.stream()
                .filter(override -> pathMatcher.match(override.pattern(), path))
                .map(ModeOverride::mode)
                .findFirst()
                .or(this::globalMode);
    }

    public List<ModeOverride> getActiveOverrides() {

        purgeExpired();
        List<ModeOverride> active = new ArrayList<>();
        ModeOverride global = globalOverride.get();
        if (Objects.nonNull(global)) {

            active.add(global);
        }
        active.addAll(patternOverrides);
        return List.copyOf(active);
    }

    public void clearGlobalOverride() {

        globalOverride.set(null);
    }

    public void clearPatternOverride(String pattern) {

        patternOverrides.removeIf(override -> Objects.equals(override.pattern(), pattern));
    }

    public void clearAll() {

        globalOverride.set(null);
        patternOverrides.clear();
    }

    private Optional<HttpLogMode> globalMode() {

        return Optional.ofNullable(globalOverride.get()).map(ModeOverride::mode);
    }

    private ModeOverride newOverride(String pattern, HttpLogMode mode, Duration ttl) {

        Objects.requireNonNull(mode, "mode must not be null");
        validateTtl(mode, ttl);
        Instant expiresAt = Objects.isNull(ttl) ? null : clock.instant().plus(ttl);
        return new ModeOverride(pattern, mode, expiresAt);
    }

    private void validateTtl(HttpLogMode mode, Duration ttl) {

        if (Objects.nonNull(ttl) && (ttl.isZero() || ttl.isNegative())) {

            throw new IllegalArgumentException("ttl must be positive");
        }
        if (properties.isRequireTtlForFullLogging() && mode == HttpLogMode.FULL && Objects.isNull(ttl)) {

            throw new IllegalArgumentException(
                    "FULL logging override requires a TTL because require-ttl-for-full-logging=true");
        }
    }

    private void purgeExpired() {

        Instant now = clock.instant();
        ModeOverride global = globalOverride.get();
        if (Objects.nonNull(global) && global.isExpired(now)) {

            globalOverride.compareAndSet(global, null);
        }
        patternOverrides.removeIf(override -> override.isExpired(now));
    }

}
