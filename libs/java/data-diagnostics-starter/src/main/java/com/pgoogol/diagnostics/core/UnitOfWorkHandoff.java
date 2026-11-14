package com.pgoogol.diagnostics.core;

/**
 * Uchwyt jednostki pracy do przekazania innemu wątkowi. Bierze się go w wątku, który ma
 * otwartą jednostkę ({@link DiagnosticsEngine#handoff()}), a zużywa w wątku wykonawczym
 * ({@link DiagnosticsEngine#join(UnitOfWorkHandoff)}).
 */
public final class UnitOfWorkHandoff {

    private final RunningUnit running;

    UnitOfWorkHandoff(RunningUnit running) {

        this.running = running;
    }

    RunningUnit running() {

        return running;
    }
}
