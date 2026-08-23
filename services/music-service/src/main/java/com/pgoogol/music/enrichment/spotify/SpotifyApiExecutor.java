package com.pgoogol.music.enrichment.spotify;

import com.pgoogol.music.common.ExternalServiceException;
import com.pgoogol.music.common.ForbiddenException;
import com.pgoogol.music.common.NotFoundException;
import com.pgoogol.music.common.RateLimitedException;
import com.pgoogol.music.common.ratelimit.ApiCallGuard;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wspólne wejście do API Spotify: jeden limiter na całe konto (osobne limitery
 * w każdym kliencie zwielokrotniłyby dozwolony ruch) + tłumaczenie błędów HTTP
 * na wyjątki domenowe. 429 honoruje {@code Retry-After}, 5xx/timeout jest
 * ponawiany, 404 i 403 nie.
 */
@Component
public class SpotifyApiExecutor {

    private final ApiCallGuard guard;

    public SpotifyApiExecutor(SpotifyProperties properties) {

        this.guard = ApiCallGuard.of("spotify", properties.requestsPerSecond());
    }

    /** {@code resource} trafia do komunikatu 404 — np. „playlista 37i9dQ…". */
    public <T> T call(String resource, Supplier<T> call) {

        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(call, "call");
        return guard.execute(() -> {

            try {

                return call.get();
            } catch (HttpClientErrorException.TooManyRequests ex) {

                throw new RateLimitedException("SPOTIFY_RATE_LIMITED",
                    "Spotify ograniczył liczbę zapytań", retryAfter(ex));
            } catch (HttpClientErrorException.NotFound ex) {

                throw new NotFoundException("SPOTIFY_RESOURCE_NOT_FOUND",
                    "Spotify nie zna zasobu: %s".formatted(resource));
            } catch (HttpClientErrorException.Forbidden ex) {

                throw new ForbiddenException("SPOTIFY_FORBIDDEN",
                    "Spotify odmówił dostępu do zasobu: %s".formatted(resource));
            } catch (HttpServerErrorException | ResourceAccessException ex) {

                throw new ExternalServiceException("SPOTIFY_UNAVAILABLE",
                    "Spotify API niedostępne", ex);
            }
        });
    }

    private Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {

        return Optional.ofNullable(ex.getResponseHeaders())
            .map(headers -> headers.getFirst("Retry-After"))
            .map(Long::parseLong)
            .map(Duration::ofSeconds)
            .orElse(null);
    }
}
