package com.pgoogol.diagnostics.core.report;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limit częstości wniosków w logu: ten sam odcisk najwyżej raz na okno. Gorąca ścieżka
 * z N+1 potrafi dać ten sam wniosek setki razy na sekundę, a log ma zostać czytelny;
 * metryki i bufor ostatnich wniosków liczą każde wystąpienie niezależnie od tego limitu.
 *
 * <p>Okno zerowe wyłącza limit. Pamiętanych odcisków jest najwyżej {@value #MAX_TRACKED};
 * po przekroczeniu mapa zaczyna od nowa, co najwyżej przepuszczając kilka powtórek.</p>
 */
public class FindingRateLimiter {

    static final int MAX_TRACKED = 10_000;

    private final long windowNanos;

    private final LongSupplier nanoClock;

    private final Map<String, Slot> slots = new ConcurrentHashMap<>();

    public FindingRateLimiter(Duration window) {

        this(window, System::nanoTime);
    }

    FindingRateLimiter(Duration window, LongSupplier nanoClock) {

        Objects.requireNonNull(window, "okno limitu jest wymagane");
        if (window.isNegative()) {

            throw new IllegalArgumentException("okno limitu nie może być ujemne: " + window);
        }
        this.windowNanos = window.toNanos();
        this.nanoClock = nanoClock;
    }

    /**
     * Pyta o zgodę na wypisanie wniosku.
     *
     * @return liczba wystąpień pominiętych od poprzedniego wypisania, gdy wolno wypisać;
     *     puste, gdy to wystąpienie trzeba pominąć
     */
    public OptionalLong tryAcquire(String fingerprint) {

        if (windowNanos == 0) {

            return OptionalLong.of(0);
        }
        if (slots.size() >= MAX_TRACKED) {

            slots.clear();
        }
        long now = nanoClock.getAsLong();
        Slot slot = slots.computeIfAbsent(fingerprint, key -> new Slot(now - windowNanos));
        return slot.tryAcquire(now, windowNanos);
    }

    /** Stan jednego odcisku; synchronizowany krótko, bez operacji blokujących w środku. */
    private static final class Slot {

        private long lastEmitted;

        private long suppressed;

        private Slot(long lastEmitted) {

            this.lastEmitted = lastEmitted;
        }

        private synchronized OptionalLong tryAcquire(long now, long windowNanos) {

            if (now - lastEmitted < windowNanos) {

                suppressed++;
                return OptionalLong.empty();
            }
            long skipped = suppressed;
            suppressed = 0;
            lastEmitted = now;
            return OptionalLong.of(skipped);
        }
    }
}
