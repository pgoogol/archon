package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Jedno wypożyczenie połączenia z puli, od pobrania do oddania.
 *
 * @param waitTime   czas czekania na połączenie w {@code getConnection}
 * @param holdTime   czas od pobrania do {@code close}
 * @param sqlTime    łączny czas zapytań wykonanych na tym połączeniu
 * @param operations liczba tych zapytań
 */
public record ConnectionEvent(Duration waitTime, Duration holdTime, Duration sqlTime, long operations)
    implements ActivityEvent {

    public ConnectionEvent {

        Objects.requireNonNull(waitTime, "czas czekania jest wymagany");
        Objects.requireNonNull(holdTime, "czas trzymania jest wymagany");
        Objects.requireNonNull(sqlTime, "czas zapytań jest wymagany");
    }

    /** Jaki procent czasu trzymania połączenie faktycznie pracowało; 100, gdy trzymanie trwało zero. */
    public long sqlSharePercent() {

        long holdNanos = holdTime.toNanos();
        if (holdNanos == 0) {

            return 100;
        }
        return Math.min(100, sqlTime.toNanos() * 100 / holdNanos);
    }
}
