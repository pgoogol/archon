package com.pgoogol.testdiagnostics.core;

import java.util.function.LongSupplier;

/** Zegar w nanosekundach przesuwany ręcznie; testy czasu bez czekania. */
public final class ManualClock implements LongSupplier {

    private long nanos = 1_000_000_000L;

    @Override
    public long getAsLong() {

        return nanos;
    }

    public void advanceMillis(long millis) {

        nanos += millis * 1_000_000L;
    }
}
