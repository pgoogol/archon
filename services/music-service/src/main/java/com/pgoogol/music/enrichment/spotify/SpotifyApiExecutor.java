package com.pgoogol.music.enrichment.spotify;

import com.pgoogol.music.common.ErrorCodes;
import com.pgoogol.music.common.ExceptionMessageConstants;
import com.pgoogol.music.common.ExternalServiceException;
import com.pgoogol.music.common.ForbiddenException;
import com.pgoogol.music.common.NotFoundException;
import com.pgoogol.music.common.RateLimitedException;
import com.pgoogol.music.common.ratelimit.ApiCallGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Wspólne wejście do API Spotify: jeden limiter na całe konto (osobne limitery
 * w każdym kliencie zwielokrotniłyby dozwolony ruch) + tłumaczenie błędów HTTP
 * na wyjątki domenowe. 429 honoruje {@code Retry-After}, 5xx/timeout jest
 * ponawiany, 404 i 403 nie.
 *
 * <p><b>Bramka kwoty.</b> Kwota Web API liczona jest per konto dewelopera i po
 * jej wyczerpaniu Spotify odpowiada 429 z {@code Retry-After} rzędu godzin.
 * Dobijanie się w tym czasie nic nie daje, a każde kolejne żądanie to kolejne
 * odrzucenie — więc pierwsze takie 429 zamyka bramkę i do czasu jej otwarcia
 * wywołania padają lokalnie, bez ruchu do Spotify. Stan jest w pamięci: po
 * restarcie pierwsze żądanie znów pójdzie do API i, jeśli blokada trwa, po
 * prostu ją odtworzy.</p>
 */
@Component
public class SpotifyApiExecutor {

    private static final Logger log = LoggerFactory.getLogger(SpotifyApiExecutor.class);

    private final ApiCallGuard guard;
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>();

    public SpotifyApiExecutor(SpotifyProperties properties) {

        this.guard = ApiCallGuard.of("spotify", properties.requestsPerSecond());
    }

    /** {@code resource} trafia do komunikatu 404 — np. „playlista 37i9dQ…". */
    public <T> T call(String resource, Supplier<T> call) {

        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(call, "call");
        requireOpenQuota();
        return guard.execute(() -> {

            try {

                return call.get();
            } catch (HttpClientErrorException.TooManyRequests ex) {

                throw rateLimited(ex);
            } catch (HttpClientErrorException.NotFound ex) {

                String message = ExceptionMessageConstants.SPOTIFY_RESOURCE_NOT_FOUND
                    .formatted(resource);
                throw new NotFoundException(ErrorCodes.SPOTIFY_RESOURCE_NOT_FOUND, message);
            } catch (HttpClientErrorException.Forbidden ex) {

                String message = ExceptionMessageConstants.SPOTIFY_FORBIDDEN.formatted(resource);
                throw new ForbiddenException(ErrorCodes.SPOTIFY_FORBIDDEN, message);
            } catch (HttpServerErrorException | ResourceAccessException ex) {

                throw new ExternalServiceException(ErrorCodes.SPOTIFY_UNAVAILABLE,
                    ExceptionMessageConstants.SPOTIFY_UNAVAILABLE, ex);
            }
        });
    }

    /** Do kiedy wstrzymany jest ruch do Spotify; puste, gdy nic nie blokuje. */
    public Optional<Instant> blockedUntil() {

        Instant until = blockedUntil.get();
        if (Objects.isNull(until) || !Instant.now().isBefore(until)) {

            return Optional.empty();
        }
        return Optional.of(until);
    }

    private void requireOpenQuota() {

        Optional<Instant> until = blockedUntil();
        if (until.isEmpty()) {

            return;
        }
        Instant resetsAt = until.get();
        Duration remaining = Duration.between(Instant.now(), resetsAt);
        String message = ExceptionMessageConstants.SPOTIFY_QUOTA_EXCEEDED.formatted(resetsAt);
        throw new RateLimitedException(ErrorCodes.SPOTIFY_QUOTA_EXCEEDED, message, remaining);
    }

    private RateLimitedException rateLimited(HttpClientErrorException.TooManyRequests ex) {

        Duration retryAfter = retryAfter(ex);
        if (!isQuotaExhausted(retryAfter)) {

            return new RateLimitedException(ErrorCodes.SPOTIFY_RATE_LIMITED,
                ExceptionMessageConstants.SPOTIFY_RATE_LIMITED, retryAfter);
        }
        Instant resetsAt = Instant.now().plus(retryAfter);
        blockedUntil.set(resetsAt);
        log.warn("""
            Kwota Spotify wyczerpana — ruch wstrzymany do {}; kolejne wywołania \
            odrzucamy lokalnie, żeby nie przedłużać blokady""", resetsAt);
        String message = ExceptionMessageConstants.SPOTIFY_QUOTA_EXCEEDED.formatted(resetsAt);
        return new RateLimitedException(ErrorCodes.SPOTIFY_QUOTA_EXCEEDED, message, retryAfter);
    }

    /**
     * Przerwa dłuższa niż ta, którą honoruje retry, to nie chwilowe przeciążenie,
     * tylko wyczerpany budżet konta — dopiero taka zamyka bramkę.
     */
    private boolean isQuotaExhausted(@Nullable Duration retryAfter) {

        if (Objects.isNull(retryAfter)) {

            return false;
        }
        return retryAfter.compareTo(ApiCallGuard.MAX_HONORED_RETRY_AFTER) > 0;
    }

    @Nullable
    private Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {

        return Optional.ofNullable(ex.getResponseHeaders())
            .map(headers -> headers.getFirst("Retry-After"))
            .map(Long::parseLong)
            .map(Duration::ofSeconds)
            .orElse(null);
    }
}
