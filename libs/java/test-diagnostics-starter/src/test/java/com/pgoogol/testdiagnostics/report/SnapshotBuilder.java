package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.ClassRecord;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.FailureRecord;
import com.pgoogol.testdiagnostics.core.MemorySnapshot;
import com.pgoogol.testdiagnostics.core.MemorySnapshotFixtures;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import com.pgoogol.testdiagnostics.core.TestTiming;

import java.util.ArrayList;
import java.util.List;

/** Migawka przebiegu składana ręcznie, żeby raport dało się sprawdzić bez uruchamiania testów. */
final class SnapshotBuilder {

    private long wallMillis = 10_000;

    private final List<ClassRecord> classes = new ArrayList<>();

    private final List<TestTiming> timings = new ArrayList<>();

    private final List<EnvironmentStart> environments = new ArrayList<>();

    private int resumeCount;

    private long resumeMillis;

    private final List<FailureRecord> failures = new ArrayList<>();

    private int classFailures;

    private MemorySnapshot memory = MemorySnapshotFixtures.calm();

    static SnapshotBuilder snapshot() {

        return new SnapshotBuilder();
    }

    SnapshotBuilder wall(long millis) {

        wallMillis = millis;
        return this;
    }

    /** Klasa z samymi udanymi testami. */
    SnapshotBuilder passingClass(String className, long durationMillis, long testMillis, int tests) {

        classes.add(new ClassRecord(className, durationMillis, testMillis, 0, tests, 0, 0));
        return this;
    }

    SnapshotBuilder withClass(ClassRecord record) {

        classes.add(record);
        return this;
    }

    SnapshotBuilder withTiming(String className, String testName, long millis) {

        timings.add(new TestTiming(className, testName, millis));
        return this;
    }

    SnapshotBuilder withEnvironment(EnvironmentStart environment) {

        environments.add(environment);
        return this;
    }

    SnapshotBuilder withResumes(int count, long millis) {

        resumeCount = count;
        resumeMillis = millis;
        return this;
    }

    SnapshotBuilder withFailure(FailureRecord failure) {

        failures.add(failure);
        if (failure.wholeClass()) {

            classFailures++;
        }
        return this;
    }

    SnapshotBuilder withMemory(MemorySnapshot snapshot) {

        memory = snapshot;
        return this;
    }

    TestRunSnapshot build() {

        return new TestRunSnapshot(wallMillis, classes, timings, environments, resumeCount, resumeMillis,
            failures, classFailures, memory);
    }
}
