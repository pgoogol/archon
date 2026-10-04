package com.pgoogol.testdiagnostics.spring.fixture;

import org.springframework.context.SmartLifecycle;

import java.util.concurrent.locks.LockSupport;

/**
 * Komponent, którego start trwa {@value #START_MILLIS} ms, jak serwer albo harmonogram.
 * Spring wstrzymuje go przy przełączeniu na inny kontekst i startuje ponownie przy
 * powrocie, więc wznowienie kontekstu ma mierzalny koszt.
 */
public class SlowLifecycle implements SmartLifecycle {

    public static final long START_MILLIS = 30;

    private volatile boolean running;

    @Override
    public void start() {

        // celowe czekanie: symuluje kosztowny start, nie czeka na wynik asynchroniczny
        long deadline = System.nanoTime() + START_MILLIS * 1_000_000L;
        while (System.nanoTime() < deadline) {

            LockSupport.parkNanos(deadline - System.nanoTime());
        }
        running = true;
    }

    @Override
    public void stop() {

        running = false;
    }

    @Override
    public boolean isRunning() {

        return running;
    }
}
