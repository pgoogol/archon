package com.pgoogol.testdiagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySnapshotTest {

    @ParameterizedTest(name = "{0} MB z 1000 MB po sprzątaniu → {1}")
    @CsvSource({
        "694, OK",
        "695, WARNING",
        "844, WARNING",
        "845, CRITICAL"
    })
    @DisplayName("sterta zajęta po sprzątaniu: od 70% limitu uwaga, od 85% krytycznie")
    void keptAfterGcHealth_followsShareOfLimit(long keptMb, Health expected) {

        // given
        MemorySnapshot memory = MemorySnapshotFixtures.withKeptAfterGc(1000, keptMb);

        // when
        Health health = memory.keptAfterGcHealth().orElseThrow();

        // then
        assertThat(health).isEqualTo(expected);
    }

    @Test
    @DisplayName("bez żadnego sprzątania ocena zajętości po sprzątaniu jest nieznana")
    void keptAfterGcHealth_whenUnknown_isEmpty() {

        // given
        MemorySnapshot memory = new MemorySnapshot(1024, 300, OptionalLong.empty(), 100, 0, 0);

        // when & then
        assertThat(memory.keptAfterGcHealth()).isEmpty();
    }

    @ParameterizedTest(name = "GC {0} ms z 10 s → {1}")
    @CsvSource({
        "449, OK",
        "450, WARNING",
        "1449, WARNING",
        "1450, CRITICAL"
    })
    @DisplayName("czas GC: od 5% przebiegu uwaga, od 15% krytycznie")
    void gcHealth_followsShareOfWallTime(long gcMillis, Health expected) {

        // given
        MemorySnapshot memory = new MemorySnapshot(1024, 300, OptionalLong.of(100), 100, gcMillis, 0);

        // when
        Health health = memory.gcHealth(10_000);

        // then
        assertThat(health).isEqualTo(expected);
    }

    @Test
    @DisplayName("przebieg bez czasu nie dzieli przez zero: udział GC to 0%")
    void gcShare_whenWallTimeZero_isZero() {

        // given
        MemorySnapshot memory = new MemorySnapshot(1024, 300, OptionalLong.of(100), 100, 50, 0);

        // when
        Share share = memory.gcShare(0);

        // then
        assertThat(share.percent()).isZero();
    }

    @Test
    @DisplayName("każde pełne sprzątanie to uwaga")
    void fullGcHealth_whenAnyFullCollection_warns() {

        // given
        MemorySnapshot memory = new MemorySnapshot(1024, 300, OptionalLong.of(100), 100, 0, 1);

        // when & then
        assertThat(memory.fullGcHealth()).isEqualTo(Health.WARNING);
    }
}
