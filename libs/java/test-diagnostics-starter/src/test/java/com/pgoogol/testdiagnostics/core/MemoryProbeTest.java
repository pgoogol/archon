package com.pgoogol.testdiagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemoryProbeTest {

    private static final long MEGABYTE = 1024L * 1024L;

    @Test
    @DisplayName("sonda na prawdziwej JVM daje wartości nieujemne, a szczyt sterty nie przekracza limitu")
    void snapshot_onRealJvm_givesSaneValues() {

        // given
        MemorySnapshot memory;

        // when
        try (MemoryProbe probe = MemoryProbe.start()) {

            memory = probe.snapshot();
        }

        // then
        assertAll(
            () -> assertThat(memory.maxHeapMb()).isPositive(),
            () -> assertThat(memory.heapPeakMb()).isBetween(0L, memory.maxHeapMb()),
            () -> assertThat(memory.nonHeapMb()).isNotNegative(),
            () -> assertThat(memory.gcMillis()).isNotNegative(),
            () -> assertThat(memory.fullGcCount()).isNotNegative());
    }

    @Test
    @DisplayName("po sprzątaniu sonda trzyma największą zajętość sterty, a pule poza stertą pomija")
    void recordAfterGc_keepsMaximumOfHeapPoolsOnly() {

        // given: bez nasłuchu, żeby prawdziwe sprzątanie w tle nie zmieniło wyniku
        MemoryProbe probe = new MemoryProbe(Set.of("Old Gen", "Survivor"), List.of());

        // when
        probe.recordAfterGc(Map.of("Old Gen", used(300), "Survivor", used(20), "Metaspace", used(900)));
        probe.recordAfterGc(Map.of("Old Gen", used(100), "Survivor", used(10)));

        // then
        MemorySnapshot memory = probe.snapshot();
        assertThat(memory.keptAfterGcMb()).hasValue(320);
    }

    @Test
    @DisplayName("drugie zamknięcie sondy nie rzuca")
    void close_whenCalledTwice_doesNotThrow() {

        // given
        MemoryProbe probe = MemoryProbe.start();
        probe.close();

        // when & then
        assertThatNoException().isThrownBy(probe::close);
    }

    private static MemoryUsage used(long megabytes) {

        long bytes = megabytes * MEGABYTE;
        return new MemoryUsage(0, bytes, bytes, -1);
    }
}
