package com.pgoogol.diagnostics.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Granica zwracana przez silnik. Wyjście ostatniej granicy zamyka jednostkę; granica wątku,
 * który tylko dołączył do cudzej jednostki, najpierw odłącza od niej swój wątek.
 */
final class EngineScope implements UnitOfWorkScope {

    private final RunningUnit running;

    private final boolean opened;

    private final Consumer<RunningUnit> closeUnit;

    private final Runnable detachThread;

    private final AtomicBoolean closed = new AtomicBoolean();

    EngineScope(RunningUnit running, boolean opened, Consumer<RunningUnit> closeUnit) {

        this(running, opened, closeUnit, () -> { });
    }

    EngineScope(RunningUnit running, boolean opened, Consumer<RunningUnit> closeUnit, Runnable detachThread) {

        this.running = running;
        this.opened = opened;
        this.closeUnit = closeUnit;
        this.detachThread = detachThread;
    }

    @Override
    public UnitOfWork unit() {

        return running.unit();
    }

    @Override
    public boolean opened() {

        return opened;
    }

    @Override
    public void close() {

        if (!closed.compareAndSet(false, true)) {

            return;
        }
        detachThread.run();
        if (running.exit()) {

            closeUnit.accept(running);
        }
    }
}
