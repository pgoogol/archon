package com.pgoogol.diagnostics.core;

import java.time.Instant;

/** Zamknięte jednostki do testów wyjść; zamyka je tu, bo {@code close} jest pakietowe. */
public final class UnitOfWorkFixtures {

    private UnitOfWorkFixtures() {
    }

    /** Jednostka HTTP {@code GET /orders} ze śladem {@code 6e1b9f}, zamknięta w {@code end}. */
    public static UnitOfWork closedHttpUnit(Instant start, Instant end) {

        UnitOfWork unit = new UnitOfWork("a1b2c3d4", "GET /orders", UnitOfWorkType.HTTP, start, "6e1b9f", 0);
        unit.close(end);
        return unit;
    }
}
