package com.pgoogol.testdiagnostics.core;

import java.util.Objects;

/** Liczniki trwającej klasy testów; zmienia je wyłącznie {@link TestRunRecorder} pod swoją blokadą. */
final class ClassState {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final long startNanos;

    private long endNanos;

    private boolean finished;

    private long testMillis;

    private long environmentMillis;

    private int tests;

    private int failed;

    private int skipped;

    ClassState(long startNanos) {

        this.startNanos = startNanos;
    }

    void finish(long now) {

        endNanos = now;
        finished = true;
    }

    void addTest(TestOutcome outcome, long millis) {

        tests++;
        testMillis += millis;
        if (Objects.equals(outcome, TestOutcome.FAILED)) {

            failed++;
        }
        if (Objects.equals(outcome, TestOutcome.ABORTED)) {

            skipped++;
        }
    }

    void addSkipped(int count) {

        tests += count;
        skipped += count;
    }

    void addEnvironmentTime(long millis) {

        environmentMillis += millis;
    }

    /** Klasa bez końca (np. przerwany fork) trwa do chwili migawki. */
    ClassRecord toRecord(String className, long now) {

        long end = now;
        if (finished) {

            end = endNanos;
        }
        long durationMillis = (end - startNanos) / NANOS_PER_MILLI;
        return new ClassRecord(className, durationMillis, testMillis, environmentMillis, tests, failed, skipped);
    }
}
