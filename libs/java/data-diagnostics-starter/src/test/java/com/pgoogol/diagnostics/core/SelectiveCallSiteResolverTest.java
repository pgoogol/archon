package com.pgoogol.diagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SelectiveCallSiteResolverTest {

    private static final DataStore POSTGRESQL = DataAccessEventFixtures.POSTGRESQL;

    private static final Duration SLOW = Duration.ofMillis(500);

    private static final Duration FAST = Duration.ofMillis(2);

    private static final CallSite CALLER = new CallSite("com.example.OrderService", "list", 42, null);

    private final AtomicReference<Optional<UnitOfWork>> current = new AtomicReference<>(Optional.of(unit("u1")));

    @Test
    @DisplayName("wolna operacja dostaje miejsce wywołania za każdym razem")
    void resolve_whenSlow_resolvesEveryTime() {

        // given
        SelectiveCallSiteResolver resolver = resolver(10, 100);

        // when
        List<Boolean> resolved = resolveTimes(resolver, 3, "select 1", SLOW);

        // then
        assertThat(resolved).containsExactly(true, true, true);
    }

    @Test
    @DisplayName("szybka operacja dostaje miejsce wywołania tylko przy N-tym powtórzeniu kształtu")
    void resolve_whenFastAndRepeated_resolvesOnlyAtNthRepetition() {

        // given
        SelectiveCallSiteResolver resolver = resolver(3, 100);

        // when
        List<Boolean> resolved = resolveTimes(resolver, 5, "select 1", FAST);

        // then
        assertThat(resolved).containsExactly(false, false, true, false, false);
    }

    @Test
    @DisplayName("nowa jednostka w wątku liczy powtórzenia od zera")
    void resolve_whenNewUnit_restartsCounting() {

        // given
        SelectiveCallSiteResolver resolver = resolver(2, 100);
        resolveTimes(resolver, 2, "select 1", FAST);
        current.set(Optional.of(unit("u2")));

        // when
        List<Boolean> resolved = resolveTimes(resolver, 2, "select 1", FAST);

        // then
        assertThat(resolved).containsExactly(false, true);
    }

    @Test
    @DisplayName("bez otwartej jednostki szybka operacja nie dostaje miejsca wywołania")
    void resolve_whenNoUnit_resolvesOnlySlowOperations() {

        // given
        current.set(Optional.empty());
        SelectiveCallSiteResolver resolver = resolver(1, 100);

        // when
        List<Boolean> resolved = resolveTimes(resolver, 2, "select 1", FAST);

        // then
        assertThat(resolved).containsExactly(false, false);
    }

    @Test
    @DisplayName("kształt ponad limit nie jest liczony, więc pamięć wątku zostaje ograniczona")
    void resolve_whenShapeOverLimit_neverResolvesIt() {

        // given
        SelectiveCallSiteResolver resolver = resolver(2, 1);
        resolveTimes(resolver, 1, "select 1", FAST);

        // when
        List<Boolean> resolved = resolveTimes(resolver, 3, "select 2", FAST);

        // then
        assertThat(resolved).containsExactly(false, false, false);
    }

    private SelectiveCallSiteResolver resolver(long repetition, int shapeLimit) {

        CallSiteResolver always = (store, shape, duration) -> Optional.of(CALLER);
        return new SelectiveCallSiteResolver(always, store -> Duration.ofMillis(100), repetition, shapeLimit,
            current::get);
    }

    private static List<Boolean> resolveTimes(CallSiteResolver resolver, int times, String shape, Duration duration) {

        return IntStream.range(0, times)
            .mapToObj(index -> resolver.resolve(POSTGRESQL, shape, duration).isPresent())
            .toList();
    }

    private static UnitOfWork unit(String id) {

        Instant start = DataAccessEventFixtures.TIMESTAMP;
        return new UnitOfWork(id, "GET /orders", UnitOfWorkType.HTTP, start, null, 0);
    }
}
