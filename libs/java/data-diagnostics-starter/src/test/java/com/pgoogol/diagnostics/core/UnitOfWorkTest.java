package com.pgoogol.diagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.pgoogol.diagnostics.core.DataAccessEventFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

class UnitOfWorkTest {

    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    @DisplayName("poniżej limitu jednostka trzyma każde zdarzenie w kolejności zapisu")
    void record_whenUnderLimit_keepsEveryEvent() {

        // given
        UnitOfWork unit = unit(10);
        DataAccessEvent first = read("select 1");
        DataAccessEvent second = read("select 2");

        // when
        unit.record(first);
        unit.record(second);

        // then
        assertAll(
            () -> assertThat(unit.events()).containsExactly(first, second),
            () -> assertThat(unit.operationCount()).isEqualTo(2),
            () -> assertThat(unit.droppedEvents()).isZero());
    }

    @Test
    @DisplayName("ponad limit jednostka trzyma pierwsze zdarzenia, a resztę tylko liczy")
    void record_whenOverLimit_keepsFirstAndCountsRest() {

        // given
        UnitOfWork unit = unit(2);

        // when
        IntStream.range(0, 5)
            .mapToObj(index -> read("select " + index))
            .forEach(unit::record);

        // then
        assertAll(
            () -> assertThat(unit.events()).extracting(DataAccessEvent::shape).containsExactly("select 0", "select 1"),
            () -> assertThat(unit.operationCount()).isEqualTo(5),
            () -> assertThat(unit.droppedEvents()).isEqualTo(3));
    }

    @Test
    @DisplayName("limit 0 wyłącza listę zdarzeń, zostaje sam licznik operacji")
    void record_whenLimitZero_keepsOnlyCounter() {

        // given
        UnitOfWork unit = unit(0);

        // when
        IntStream.range(0, 3)
            .mapToObj(index -> read("select 1"))
            .forEach(unit::record);

        // then
        assertAll(
            () -> assertThat(unit.events()).isEmpty(),
            () -> assertThat(unit.operationCount()).isEqualTo(3),
            () -> assertThat(unit.droppedEvents()).isZero());
    }

    @Test
    @DisplayName("zamknięta jednostka odrzuca operację, bo ta należy już do kogoś innego")
    void record_whenClosed_rejectsEvent() {

        // given
        UnitOfWork unit = unit(10);
        unit.close(START.plusMillis(5));

        // when
        boolean accepted = unit.record(read("select 1"));

        // then
        assertAll(
            () -> assertThat(accepted).isFalse(),
            () -> assertThat(unit.operationCount()).isZero());
    }

    @Test
    @DisplayName("czas jednostki liczy się od otwarcia do pierwszego zamknięcia")
    void duration_whenClosedTwice_keepsFirstClose() {

        // given
        UnitOfWork unit = unit(10);
        unit.close(START.plusMillis(40));
        unit.close(START.plusMillis(90));

        // when & then
        assertThat(unit.duration()).contains(Duration.ofMillis(40));
    }

    @Test
    @DisplayName("zdarzenia z wielu wątków naraz są policzone co do jednego i nie przekraczają limitu")
    void record_whenManyThreadsRecord_countsEveryOperation() throws InterruptedException {

        // given
        UnitOfWork unit = unit(1_000);
        int threads = 8;
        int perThread = 500;
        CountDownLatch startGate = new CountDownLatch(1);

        // when
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            IntStream.range(0, threads).forEach(thread -> executor.submit(() -> recordAfter(startGate, unit, perThread)));
            startGate.countDown();
        }

        // then
        assertAll(
            () -> assertThat(unit.operationCount()).isEqualTo(4_000),
            () -> assertThat(unit.events()).hasSize(1_000),
            () -> assertThat(unit.droppedEvents()).isEqualTo(3_000));
    }

    @Test
    @DisplayName("ujemny limit zdarzeń jest odrzucany")
    void constructor_whenEventLimitNegative_fails() {

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> unit(-1))
            .withMessageContaining("limit zdarzeń");
    }

    /** Czeka na wspólny start, żeby wątki naprawdę zapisywały naraz; zwraca liczbę zapisów. */
    private static int recordAfter(CountDownLatch startGate, UnitOfWork unit, int count) throws InterruptedException {

        startGate.await();
        List<DataAccessEvent> events = IntStream.range(0, count)
            .mapToObj(index -> read("select 1"))
            .toList();
        events.forEach(unit::record);
        return events.size();
    }

    private static UnitOfWork unit(int eventLimit) {

        return new UnitOfWork("a1b2c3d4", "GET /orders", UnitOfWorkType.HTTP, START, null, eventLimit);
    }
}
