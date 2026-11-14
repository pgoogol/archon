package com.pgoogol.diagnostics.core.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class FindingRateLimiterTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    @DisplayName("ten sam odcisk przechodzi raz na okno, a po oknie podaje liczbę pominiętych")
    void tryAcquire_whenRepeatedWithinWindow_suppressesAndReportsSkipped() {

        // given
        FindingRateLimiter limiter = new FindingRateLimiter(Duration.ofMinutes(1), now::get);

        // when
        OptionalLong first = limiter.tryAcquire("a3f9");
        OptionalLong second = limiter.tryAcquire("a3f9");
        OptionalLong third = limiter.tryAcquire("a3f9");
        now.addAndGet(Duration.ofMinutes(1).toNanos());
        OptionalLong afterWindow = limiter.tryAcquire("a3f9");

        // then
        assertAll(
            () -> assertThat(first).hasValue(0),
            () -> assertThat(second).isEmpty(),
            () -> assertThat(third).isEmpty(),
            () -> assertThat(afterWindow).hasValue(2));
    }

    @Test
    @DisplayName("różne odciski nie blokują się nawzajem")
    void tryAcquire_whenDifferentFingerprints_allowsEach() {

        // given
        FindingRateLimiter limiter = new FindingRateLimiter(Duration.ofMinutes(1), now::get);

        // when
        OptionalLong first = limiter.tryAcquire("a3f9");
        OptionalLong other = limiter.tryAcquire("b7c2");

        // then
        assertThat(first).hasValue(0);
        assertThat(other).hasValue(0);
    }

    @Test
    @DisplayName("okno zerowe wyłącza limit")
    void tryAcquire_whenWindowZero_alwaysAllows() {

        // given
        FindingRateLimiter limiter = new FindingRateLimiter(Duration.ZERO, now::get);

        // when
        long allowed = IntStream.range(0, 5)
            .filter(index -> limiter.tryAcquire("a3f9").isPresent())
            .count();

        // then
        assertThat(allowed).isEqualTo(5);
    }

    @Test
    @DisplayName("liczba pamiętanych odcisków jest ograniczona, a limiter działa dalej po przepełnieniu")
    void tryAcquire_whenTooManyFingerprints_keepsWorking() {

        // given
        FindingRateLimiter limiter = new FindingRateLimiter(Duration.ofMinutes(1), now::get);

        // when
        long allowed = IntStream.range(0, FindingRateLimiter.MAX_TRACKED + 10)
            .filter(index -> limiter.tryAcquire("fp-" + index).isPresent())
            .count();

        // then
        assertThat(allowed).isEqualTo(FindingRateLimiter.MAX_TRACKED + 10);
    }
}
