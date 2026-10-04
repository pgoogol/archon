package com.pgoogol.testdiagnostics.junit;

import com.pgoogol.testdiagnostics.core.ClassRecord;
import com.pgoogol.testdiagnostics.core.FailureRecord;
import com.pgoogol.testdiagnostics.core.MemorySnapshotFixtures;
import com.pgoogol.testdiagnostics.core.RunSettings;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import com.pgoogol.testdiagnostics.junit.fixture.AbortedSetupCases;
import com.pgoogol.testdiagnostics.junit.fixture.DisabledCases;
import com.pgoogol.testdiagnostics.junit.fixture.FailedSetupCases;
import com.pgoogol.testdiagnostics.junit.fixture.MixedOutcomeCases;
import com.pgoogol.testdiagnostics.junit.fixture.ParallelFirstCases;
import com.pgoogol.testdiagnostics.junit.fixture.ParallelMeeting;
import com.pgoogol.testdiagnostics.junit.fixture.ParallelSecondCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestDiagnosticsExecutionListenerTest {

    /** Klasy równolegle, metody w wątku swojej klasy. */
    private static final Map<String, String> PARALLEL_CLASSES = Map.of(
        "junit.jupiter.execution.parallel.enabled", "true",
        "junit.jupiter.execution.parallel.mode.default", "same_thread",
        "junit.jupiter.execution.parallel.mode.classes.default", "concurrent",
        "junit.jupiter.execution.parallel.config.strategy", "fixed",
        "junit.jupiter.execution.parallel.config.fixed.parallelism", "4");

    private final TestRunRecorder recorder = new TestRunRecorder(System::nanoTime);

    @Test
    @DisplayName("wyniki testów: udany, z błędem, przerwany, wyłączony, parametryzowane i @Nested w jednej klasie")
    void execution_whenMixedOutcomes_countsEveryTestInItsTopLevelClass() {

        // when
        TestRunSnapshot snapshot = run(Map.of(), MixedOutcomeCases.class);

        // then: 7 testów, z czego 1 z błędem, a przerwany i wyłączony to pominięte
        assertAll(
            () -> assertThat(snapshot.classes())
                .extracting(ClassRecord::className, ClassRecord::tests, ClassRecord::failed, ClassRecord::skipped)
                .containsExactly(tuple(MixedOutcomeCases.class.getName(), 7, 1, 2)),
            () -> assertThat(snapshot.failures()).containsExactly(
                new FailureRecord(MixedOutcomeCases.class.getName(), "fails()", "AssertionError: expected 400")));
    }

    @Test
    @DisplayName("klasa z @Disabled: wszystkie jej testy liczą się jako pominięte")
    void execution_whenClassDisabled_countsItsTestsAsSkipped() {

        // when
        TestRunSnapshot snapshot = run(Map.of(), DisabledCases.class);

        // then
        assertThat(snapshot.classes())
            .extracting(ClassRecord::className, ClassRecord::tests, ClassRecord::skipped)
            .containsExactly(tuple(DisabledCases.class.getName(), 2, 2));
    }

    @Test
    @DisplayName("klasa przerwana założeniem w @BeforeAll: testy pominięte, żadnego problemu")
    void execution_whenSetupAborted_countsTestsAsSkippedWithoutProblem() {

        // when
        TestRunSnapshot snapshot = run(Map.of(), AbortedSetupCases.class);

        // then
        assertAll(
            () -> assertThat(snapshot.skipped()).isEqualTo(2),
            () -> assertThat(snapshot.problems()).isZero());
    }

    @Test
    @DisplayName("wyjątek w @BeforeAll to błąd całej klasy z opisem wyjątku")
    void execution_whenSetupFails_recordsWholeClassFailure() {

        // when
        TestRunSnapshot snapshot = run(Map.of(), FailedSetupCases.class);

        // then
        assertAll(
            () -> assertThat(snapshot.classFailures()).isEqualTo(1),
            () -> assertThat(snapshot.failures()).containsExactly(
                FailureRecord.ofWholeClass(FailedSetupCases.class.getName(), "IllegalStateException: setup broke")));
    }

    @Test
    @DisplayName("klasy wykonywane równolegle: każdy test trafia do swojej klasy")
    void execution_whenClassesRunInParallel_attributesTestsToOwnClass() {

        // given: testy obu klas czekają na siebie, więc przechodzą tylko przy wykonaniu naraz
        ParallelMeeting.reset();

        // when
        TestRunSnapshot snapshot = run(PARALLEL_CLASSES, ParallelFirstCases.class, ParallelSecondCases.class);

        // then
        assertThat(snapshot.classes())
            .extracting(ClassRecord::className, ClassRecord::tests, ClassRecord::failed)
            .containsExactlyInAnyOrder(
                tuple(ParallelFirstCases.class.getName(), 1, 0),
                tuple(ParallelSecondCases.class.getName(), 1, 0));
    }

    @Test
    @DisplayName("etykieta, język i wyłącznik przychodzą z parametrów JUnit")
    void execution_readsSettingsFromConfigurationParameters() {

        // given
        Map<String, String> parameters = Map.of(
            RunSettings.LABEL_KEY, "nightly",
            RunSettings.LOCALE_KEY, "pl",
            RunSettings.ENABLED_KEY, "false");

        // when
        run(parameters, DisabledCases.class);

        // then
        assertThat(recorder.settings()).contains(new RunSettings("nightly", "pl", false));
    }

    @Test
    @DisplayName("biała etykieta daje etykietę domyślną, a raport jest włączony")
    void execution_whenLabelBlank_usesDefaults() {

        // when
        run(Map.of(RunSettings.LABEL_KEY, "  "), DisabledCases.class);

        // then
        assertThat(recorder.settings()).hasValueSatisfying(settings -> assertAll(
            () -> assertThat(settings.label()).isEqualTo(RunSettings.DEFAULT_LABEL),
            () -> assertThat(settings.enabled()).isTrue()));
    }

    @Test
    @DisplayName("opis błędu: nazwa wyjątku i pierwsza linia, najwyżej 160 znaków")
    void describe_keepsFirstLineAndCutsAt160() {

        // given
        IllegalStateException multiline = new IllegalStateException("first\nsecond");
        IllegalStateException withoutMessage = new IllegalStateException();
        AssertionError tooLong = new AssertionError("x".repeat(300));

        // when
        String first = TestDiagnosticsExecutionListener.describe(multiline);
        String bare = TestDiagnosticsExecutionListener.describe(withoutMessage);
        String cut = TestDiagnosticsExecutionListener.describe(tooLong);

        // then
        assertAll(
            () -> assertThat(first).isEqualTo("IllegalStateException: first"),
            () -> assertThat(bare).isEqualTo("IllegalStateException"),
            () -> assertThat(cut).hasSize(160).startsWith("AssertionError: xxx").endsWith("x..."));
    }

    /** Uruchamia klasy z tym rejestratorem jako aktywnym, żeby nie dopisać ich do raportu zewnętrznego przebiegu. */
    private TestRunSnapshot run(Map<String, String> parameters, Class<?>... classes) {

        TestDiagnosticsExecutionListener listener = new TestDiagnosticsExecutionListener();
        LauncherConfig config = NestedLauncher.isolated().addTestExecutionListeners(listener).build();
        try (TestRunRecorder.Activation ignored = recorder.activate()) {

            NestedLauncher.execute(config, parameters, classes);
        }
        return recorder.snapshot(MemorySnapshotFixtures.calm());
    }
}
