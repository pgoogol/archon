package com.pgoogol.llm.provider;

import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.exception.LlmRateLimitedException;
import com.pgoogol.llm.exception.LlmRequestRejectedException;
import com.pgoogol.llm.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCallGuardTest {

    @Test
    void execute_whenProviderIsUnavailable_retriesUpToConfiguredAttempts() {

        // given
        AtomicInteger calls = new AtomicInteger();
        LlmCallGuard guard = new LlmCallGuard("test", config(3, Duration.ofSeconds(30)));

        // when, then
        assertThatThrownBy(() -> guard.execute(() -> {

            calls.incrementAndGet();
            throw new LlmUnavailableException("nie odpowiada", null);
        })).isInstanceOf(LlmUnavailableException.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void execute_whenRequestIsRejected_doesNotRetry() {

        // given: ten sam zły klucz wróci tym samym błędem i tym samym rachunkiem
        AtomicInteger calls = new AtomicInteger();
        LlmCallGuard guard = new LlmCallGuard("test", config(3, Duration.ofSeconds(30)));

        // when, then
        assertThatThrownBy(() -> guard.execute(() -> {

            calls.incrementAndGet();
            throw new LlmRequestRejectedException("odrzucone", 400, null);
        })).isInstanceOf(LlmRequestRejectedException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_whenRetryAfterFitsInWindow_retries() {

        // given
        AtomicInteger calls = new AtomicInteger();
        LlmCallGuard guard = new LlmCallGuard("test", config(2, Duration.ofSeconds(30)));

        // when
        String result = guard.execute(() -> {

            if (calls.incrementAndGet() == 1) {

                throw new LlmRateLimitedException("limit", Duration.ofMillis(10));
            }
            return "gotowe";
        });

        // then
        assertThat(result).isEqualTo("gotowe");
        assertThat(calls).hasValue(2);
    }

    @Test
    void execute_whenRetryAfterExceedsWindow_handsTheDecisionToTheCaller() {

        // given: „ponów za 22 godziny" to wyczerpany budżet, nie chwilowe przeciążenie
        AtomicInteger calls = new AtomicInteger();
        LlmCallGuard guard = new LlmCallGuard("test", config(3, Duration.ofSeconds(30)));

        // when, then
        assertThatThrownBy(() -> guard.execute(() -> {

            calls.incrementAndGet();
            throw new LlmRateLimitedException("limit", Duration.ofHours(22));
        })).isInstanceOf(LlmRateLimitedException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_whenCallSucceeds_returnsWithoutRetrying() {

        // given
        AtomicInteger calls = new AtomicInteger();
        LlmCallGuard guard = new LlmCallGuard("test", config(3, Duration.ofSeconds(30)));

        // when
        String result = guard.execute(() -> {

            calls.incrementAndGet();
            return "gotowe";
        });

        // then
        assertThat(result).isEqualTo("gotowe");
        assertThat(calls).hasValue(1);
    }

    private LlmClientProperties config(int maxAttempts, Duration maxHonoredRetryAfter) {

        LlmClientProperties config = new LlmClientProperties();
        config.setMaxAttempts(maxAttempts);
        config.setInitialBackoff(Duration.ofMillis(5));
        config.setMaxHonoredRetryAfter(maxHonoredRetryAfter);
        config.setRequestsPerSecond(100);
        return config;
    }
}
