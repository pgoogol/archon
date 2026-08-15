package com.pgoogol.httpexchangelogger.admin;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import com.pgoogol.httpexchangelogger.runtime.ModeOverride;
import com.pgoogol.httpexchangelogger.runtime.RuntimeModeOverrideManager;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Actuator endpoint for inspecting the logger state and managing temporary
 * mode overrides at runtime.
 *
 * <p>Not exposed by default — it becomes reachable only when the application
 * opts in via {@code management.endpoints.web.exposure.include}. Access should
 * stay restricted to operators (internal network or an ADMIN role).
 *
 * <ul>
 *   <li>{@code GET} — current configuration and active overrides,</li>
 *   <li>{@code POST {"mode": "FULL", "pattern": "/api/orders/**", "ttlSeconds": 600}}
 *       — sets an override; {@code pattern} omitted means global,</li>
 *   <li>{@code DELETE ?pattern=...} — clears one override, no pattern clears all.</li>
 * </ul>
 */
@Endpoint(id = "httpexchangelogger")
public class HttpExchangeLoggerEndpoint {

    private final HttpExchangeLoggerProperties properties;
    private final RuntimeModeOverrideManager overrideManager;

    public HttpExchangeLoggerEndpoint(HttpExchangeLoggerProperties properties,
                                      RuntimeModeOverrideManager overrideManager) {

        this.properties = properties;
        this.overrideManager = overrideManager;
    }

    @ReadOperation
    public HttpExchangeLoggerStatus status() {

        return new HttpExchangeLoggerStatus(
                properties.isEnabled(),
                properties.getDefaultMode(),
                properties.getSampling().getRate(),
                properties.isRequireTtlForFullLogging(),
                overrideManager.getActiveOverrides());
    }

    @WriteOperation
    public HttpExchangeLoggerStatus setOverride(String mode,
                                                @Nullable String pattern,
                                                @Nullable Long ttlSeconds) {

        HttpLogMode logMode = parseMode(mode);
        Duration ttl = Objects.isNull(ttlSeconds) ? null : Duration.ofSeconds(ttlSeconds);
        try {

            if (StringUtils.hasText(pattern)) {

                overrideManager.setPatternOverride(pattern, logMode, ttl);
            } else {

                overrideManager.setGlobalOverride(logMode, ttl);
            }
        } catch (IllegalArgumentException ex) {

            throw new InvalidEndpointRequestException(ex.getMessage(), ex.getMessage());
        }
        return status();
    }

    @DeleteOperation
    public HttpExchangeLoggerStatus clearOverrides(@Nullable String pattern) {

        if (StringUtils.hasText(pattern)) {

            overrideManager.clearPatternOverride(pattern);
        } else {

            overrideManager.clearAll();
        }
        return status();
    }

    private HttpLogMode parseMode(String mode) {

        if (!StringUtils.hasText(mode)) {

            throw new InvalidEndpointRequestException("mode is required", "mode is required");
        }
        try {

            return HttpLogMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {

            String reason = "unknown mode '%s', expected one of OFF, BASIC, LIMITED, FULL".formatted(mode);
            throw new InvalidEndpointRequestException(reason, reason);
        }
    }

    public record HttpExchangeLoggerStatus(boolean enabled,
                                           HttpLogMode defaultMode,
                                           double samplingRate,
                                           boolean requireTtlForFullLogging,
                                           List<ModeOverride> activeOverrides) {

    }

}
