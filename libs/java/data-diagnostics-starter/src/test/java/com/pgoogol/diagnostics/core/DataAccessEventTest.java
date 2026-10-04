package com.pgoogol.diagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DataAccessEventTest {

    private static final DataStore POSTGRESQL = new DataStore("postgresql");

    private static final String SHAPE = "select * from orders where id = ?";

    @Test
    @DisplayName("ujemny czas operacji oznacza błąd pomiaru, więc zdarzenie jest odrzucane")
    void constructor_whenDurationNegative_fails() {

        // given
        Duration negative = Duration.ofMillis(-1);

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> event(negative, 0))
            .withMessageContaining("czas operacji");
    }

    @Test
    @DisplayName("ujemny rozmiar batcha jest odrzucany — 0 oznacza operację poza batchem")
    void constructor_whenBatchSizeNegative_fails() {

        // given
        Duration duration = Duration.ofMillis(3);

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> event(duration, -1))
            .withMessageContaining("rozmiar batcha");
    }

    private static DataAccessEvent event(Duration duration, int batchSize) {

        Instant timestamp = Instant.parse("2026-10-03T12:00:00Z");
        return new DataAccessEvent(POSTGRESQL, OperationKind.READ, SHAPE, List.of(), SHAPE, duration, true, batchSize,
            null, timestamp);
    }
}
