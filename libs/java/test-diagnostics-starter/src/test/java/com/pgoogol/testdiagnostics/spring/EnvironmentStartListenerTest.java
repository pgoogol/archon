package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.core.ClassRecord;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.MemorySnapshotFixtures;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import com.pgoogol.testdiagnostics.junit.NestedLauncher;
import com.pgoogol.testdiagnostics.junit.TestDiagnosticsExecutionListener;
import com.pgoogol.testdiagnostics.spring.fixture.BrokenContextCases;
import com.pgoogol.testdiagnostics.spring.fixture.ResumeAFirstCases;
import com.pgoogol.testdiagnostics.spring.fixture.ResumeBSecondCases;
import com.pgoogol.testdiagnostics.spring.fixture.ResumeCThirdCases;
import com.pgoogol.testdiagnostics.spring.fixture.SharedFirstCases;
import com.pgoogol.testdiagnostics.spring.fixture.SharedSecondCases;
import com.pgoogol.testdiagnostics.spring.fixture.SlowLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Słuchacz na prawdziwych kontekstach Springa, zarejestrowany jak u konsumenta: przez
 * {@code META-INF/spring.factories}, bez ręcznego dodawania. Każdy scenariusz ma własne
 * klasy konfiguracji, bo pamięć podręczna kontekstów jest wspólna dla całej JVM.
 */
class EnvironmentStartListenerTest {

    /** Klasy w kolejności nazw, żeby przełączenia kontekstów szły w znanym porządku. */
    private static final Map<String, String> CLASSES_BY_NAME = Map.of(
        "junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer$ClassName");

    private final TestRunRecorder recorder = new TestRunRecorder(System::nanoTime);

    @Test
    @DisplayName("dwie klasy o tej samej konfiguracji: jedno środowisko z profilami, beanami i czasem w swojej klasie")
    void beforeTestClass_whenClassesShareConfiguration_startsOneEnvironment() {

        // when
        TestRunSnapshot snapshot = run(Map.of(), SharedFirstCases.class, SharedSecondCases.class);

        // then: start liczy się klasie, która go wywołała
        List<String> fixtureClasses = List.of(SharedFirstCases.class.getName(), SharedSecondCases.class.getName());
        assertThat(snapshot.environments()).singleElement().satisfies(environment -> assertAll(
            () -> assertThat(environment.testClassName()).isIn(fixtureClasses),
            () -> assertThat(environment.profiles()).containsExactly("fixture"),
            () -> assertThat(environment.beanCount()).isPositive(),
            () -> assertThat(environment.failed()).isFalse(),
            () -> assertThat(snapshot.classes())
                .filteredOn(record -> record.className().equals(environment.testClassName()))
                .singleElement()
                .extracting(ClassRecord::environmentMillis)
                .isEqualTo(environment.durationMillis())));
    }

    @Test
    @DisplayName("kontekst, który nie wstał: nieudany start w raporcie i błąd całej klasy")
    void beforeTestClass_whenContextFails_recordsFailedStart() {

        // when
        TestRunSnapshot snapshot = run(Map.of(), BrokenContextCases.class);

        // then
        assertAll(
            () -> assertThat(snapshot.environments()).singleElement()
                .extracting(EnvironmentStart::failed, EnvironmentStart::testClassName)
                .containsExactly(true, BrokenContextCases.class.getName()),
            () -> assertThat(snapshot.classFailures()).isEqualTo(1));
    }

    @Test
    @DisplayName("powrót do wstrzymanego kontekstu to wznowienie z czasem restartu komponentów")
    void beforeTestClass_whenPausedContextReturns_measuresResume() {

        // when: A uruchamia kontekst, B przełącza na inny, C wraca do pierwszego
        TestRunSnapshot snapshot = run(CLASSES_BY_NAME,
            ResumeAFirstCases.class, ResumeBSecondCases.class, ResumeCThirdCases.class);

        // then
        assertAll(
            () -> assertThat(snapshot.environments()).hasSize(2),
            () -> assertThat(snapshot.resumeCount()).isEqualTo(1),
            () -> assertThat(snapshot.resumeMillis()).isGreaterThanOrEqualTo(SlowLifecycle.START_MILLIS));
    }

    /** Klasy z tym rejestratorem jako aktywnym; słuchacz Springa przychodzi z {@code spring.factories}. */
    private TestRunSnapshot run(Map<String, String> parameters, Class<?>... classes) {

        TestDiagnosticsExecutionListener listener = new TestDiagnosticsExecutionListener();
        LauncherConfig config = NestedLauncher.isolated().addTestExecutionListeners(listener).build();
        try (TestRunRecorder.Activation ignored = recorder.activate()) {

            NestedLauncher.execute(config, parameters, classes);
        }
        return recorder.snapshot(MemorySnapshotFixtures.calm());
    }
}
