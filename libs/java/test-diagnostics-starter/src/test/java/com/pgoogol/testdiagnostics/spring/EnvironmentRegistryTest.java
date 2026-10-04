package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.MemorySnapshotFixtures;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class EnvironmentRegistryTest {

    private final TestRunRecorder recorder = new TestRunRecorder(System::nanoTime);

    private final EnvironmentRegistry registry = new EnvironmentRegistry();

    @Test
    @DisplayName("numery idą po kolei, także dla nieudanych startów, i zgadzają się z kontekstem")
    void record_numbersEnvironmentsInOrder() {

        // given
        GenericApplicationContext context = new GenericApplicationContext();

        // when
        int first = registry.record(recorder, described("test"), cause -> started("A", cause), context);
        int failed = registry.record(recorder, described("test"), cause -> EnvironmentStart.failed("B", 10, cause), null);

        // then
        assertAll(
            () -> assertThat(List.of(first, failed)).containsExactly(1, 2),
            () -> assertThat(registry.numberOf(context)).contains(1),
            () -> assertThat(recorder.snapshot(MemorySnapshotFixtures.calm()).environments()).hasSize(2));
    }

    @Test
    @DisplayName("środowisko zamknięte przez klasę i start tej samej konfiguracji: powód wskazuje tę klasę")
    void record_whenSameConfigurationAfterClose_pointsAtClosingClass() {

        // given
        registry.record(recorder, described("test"), cause -> started("A", cause), new GenericApplicationContext());
        registry.closed(1, "com.example.DirtyTest");

        // when
        registry.record(recorder, described("test"), cause -> started("B", cause), new GenericApplicationContext());

        // then
        assertThat(recorder.snapshot(MemorySnapshotFixtures.calm()).environments())
            .extracting(EnvironmentStart::cause)
            .containsExactly(EnvironmentCause.first(), new EnvironmentCause.Reloaded(1, "com.example.DirtyTest"));
    }

    @Test
    @DisplayName("nieudany opis konfiguracji daje powód nieznany z opisem błędu")
    void record_whenDescriptionFailed_isUnknown() {

        // when
        registry.record(recorder, Described.failed("IllegalStateException: boom"), cause -> started("A", cause),
            new GenericApplicationContext());

        // then
        assertThat(recorder.snapshot(MemorySnapshotFixtures.calm()).environments())
            .extracting(EnvironmentStart::cause)
            .containsExactly(new EnvironmentCause.Unknown("IllegalStateException: boom"));
    }

    @Test
    @DisplayName("dostosowania kontekstu: równe dostają ten sam wariant, różne kolejne")
    void variantOf_numbersCustomizersByEquality() {

        // given
        ContextCustomizer first = new NamedCustomizer("a");
        ContextCustomizer equalToFirst = new NamedCustomizer("a");
        ContextCustomizer second = new NamedCustomizer("b");

        // when
        List<String> variants = List.of(
            registry.variantOf(first), registry.variantOf(equalToFirst), registry.variantOf(second));

        // then
        assertThat(variants).containsExactly("NamedCustomizer #1", "NamedCustomizer #1", "NamedCustomizer #2");
    }

    private static Described described(String profile) {

        ContextDescription description = new ContextDescription(Map.of(ContextDescription.PROFILES, List.of(profile)));
        return Described.of(description);
    }

    private static EnvironmentStart started(String testClass, EnvironmentCause cause) {

        return EnvironmentStart.started(testClass, 100, List.of(), 10, cause);
    }

    /** Dostosowanie z równością po nazwie, jak większość dostosowań Springa i Boota. */
    private record NamedCustomizer(String name) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        }
    }
}
