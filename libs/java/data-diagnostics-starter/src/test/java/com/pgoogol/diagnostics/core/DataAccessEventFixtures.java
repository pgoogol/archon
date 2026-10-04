package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Zdarzenia do testów rdzenia: test podaje to, co sprawdza, resztę wypełniają sensowne
 * wartości z PostgreSQL.
 */
public final class DataAccessEventFixtures {

    public static final DataStore POSTGRESQL = new DataStore("postgresql");

    public static final Instant TIMESTAMP = Instant.parse("2026-10-03T12:00:00Z");

    private DataAccessEventFixtures() {
    }

    /** Udany odczyt poza batchem, trwający 2 ms. */
    public static DataAccessEvent read(String shape) {

        return event(OperationKind.READ, shape);
    }

    /** Udany zapis poza batchem, trwający 2 ms. */
    public static DataAccessEvent write(String shape) {

        return event(OperationKind.WRITE, shape);
    }

    private static DataAccessEvent event(OperationKind kind, String shape) {

        Duration duration = Duration.ofMillis(2);
        return new DataAccessEvent(POSTGRESQL, kind, shape, List.of(), shape, duration, true, 0, null, TIMESTAMP);
    }
}
