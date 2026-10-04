package com.pgoogol.testdiagnostics.core;

import java.util.OptionalLong;

/** Migawki pamięci do testów, w których pamięć nie jest tematem. */
public final class MemorySnapshotFixtures {

    private MemorySnapshotFixtures() {
    }

    /** Spokojny przebieg: 1 GB limitu, 400 MB szczytu, 200 MB po sprzątaniu, bez pełnego GC. */
    public static MemorySnapshot calm() {

        return new MemorySnapshot(1024, 400, OptionalLong.of(200), 120, 300, 0);
    }

    public static MemorySnapshot withKeptAfterGc(long maxHeapMb, long keptAfterGcMb) {

        return new MemorySnapshot(maxHeapMb, keptAfterGcMb, OptionalLong.of(keptAfterGcMb), 120, 0, 0);
    }
}
