package com.pgoogol.music.enrichment.spotify;

import com.pgoogol.music.common.RateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Bramka kwoty: kwota Web API liczy się per konto dewelopera, więc po jej
 * wyczerpaniu każde kolejne żądanie to pewne odrzucenie — i tylko przedłuża
 * blokadę. Wyczerpanie poznajemy po {@code Retry-After} rzędu godzin.
 */
class SpotifyApiExecutorTest {

    @Test
    void call_whenQuotaExhausted_stopsFurtherCallsBeforeTheyReachSpotify() {

        // given
        SpotifyApiExecutor executor = executor();
        AtomicInteger apiCalls = new AtomicInteger();

        // when — pierwsze 429 z odległym Retry-After zamyka bramkę
        Throwable first = catchThrowable(() -> executor.call("playlista pl-1", () -> {

            apiCalls.incrementAndGet();
            throw tooManyRequests(Duration.ofHours(22));
        }));
        Throwable second = catchThrowable(() -> executor.call("playlista pl-2",
            () -> apiCalls.incrementAndGet()));

        // then — drugie wywołanie w ogóle nie poszło do Spotify
        assertThat(first).isInstanceOf(RateLimitedException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_QUOTA_EXCEEDED");
        assertThat(second).isInstanceOf(RateLimitedException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_QUOTA_EXCEEDED");
        assertThat(apiCalls.get()).isEqualTo(1);
        assertThat(executor.blockedUntil()).isPresent();
    }

    @Test
    void call_whenRateLimitIsBrief_keepsTheGateOpen() {

        // given — chwilowe 429 to nie wyczerpana kwota
        SpotifyApiExecutor executor = executor();

        // when
        Throwable thrown = catchThrowable(() -> executor.call("playlista pl-1", () -> {

            throw tooManyRequests(Duration.ofSeconds(1));
        }));

        // then
        assertThat(thrown).isInstanceOf(RateLimitedException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_RATE_LIMITED");
        assertThat(executor.blockedUntil()).isEmpty();
        assertThat(executor.call("playlista pl-2", () -> "ok")).isEqualTo("ok");
    }

    private SpotifyApiExecutor executor() {

        SpotifyProperties properties = new SpotifyProperties("http://localhost", "http://localhost",
            "id", "secret", 100, "http://localhost/callback", "playlist-read-private");
        return new SpotifyApiExecutor(properties);
    }

    private HttpClientErrorException tooManyRequests(Duration retryAfter) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(retryAfter.toSeconds()));
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
            headers, new byte[0], StandardCharsets.UTF_8);
    }
}
