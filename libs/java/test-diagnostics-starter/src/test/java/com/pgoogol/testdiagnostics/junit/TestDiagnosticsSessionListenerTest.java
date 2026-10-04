package com.pgoogol.testdiagnostics.junit;

import com.pgoogol.testdiagnostics.core.MemorySnapshotFixtures;
import com.pgoogol.testdiagnostics.core.RunSettings;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import com.pgoogol.testdiagnostics.junit.fixture.FixtureRun;
import com.pgoogol.testdiagnostics.junit.fixture.MixedOutcomeCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherConfig;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestDiagnosticsSessionListenerTest {

    private static final String P = "[TEST-DIAGNOSTICS] ";

    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    private final PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);

    private final PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

    @Test
    @DisplayName("po sesji z testami raport idzie na wyjście z etykietą i w języku z parametrów")
    void session_whenTestsExecuted_printsReport() {

        // given
        Optional<TestRunRecorder> before = TestRunRecorder.active();
        Map<String, String> parameters = Map.of(RunSettings.LABEL_KEY, "nightly", RunSettings.LOCALE_KEY, "pl");

        // when
        execute(parameters, () -> out, MixedOutcomeCases.class);

        // then
        List<String> lines = lines(outBuffer);
        assertAll(
            () -> assertThat(lines).contains(P + " RAPORT Z TESTÓW: NIGHTLY"),
            () -> assertThat(lines).anyMatch(line -> line.startsWith(P + "DATA label=nightly tests=7 failed=1 skipped=2 ")),
            () -> assertThat(TestRunRecorder.active()).isEqualTo(before));
    }

    @Test
    @DisplayName("wyłącznik z parametrów JUnit: przebieg bez raportu")
    void session_whenDisabledByParameter_printsNothing() {

        // when
        execute(Map.of(RunSettings.ENABLED_KEY, "false"), () -> out, MixedOutcomeCases.class);

        // then
        assertThat(lines(outBuffer)).isEmpty();
    }

    @Test
    @DisplayName("wyłącznik z właściwości systemowej: sesja nie otwiera własnego przebiegu")
    void session_whenDisabledBySystemProperty_doesNotOpenRun() {

        // given: strażnik zamiast przebiegu zewnętrznego Surefire, do którego inaczej
        // trafiłyby zdarzenia zagnieżdżonego uruchomienia
        TestRunRecorder sentinel = new TestRunRecorder(System::nanoTime);
        System.setProperty(RunSettings.ENABLED_KEY, "false");

        // when
        try (TestRunRecorder.Activation ignored = sentinel.activate()) {

            execute(Map.of(), () -> out, MixedOutcomeCases.class);
        } finally {

            System.clearProperty(RunSettings.ENABLED_KEY);
        }

        // then: bez własnego przebiegu sesji testy policzył strażnik, a raportu nie ma
        TestRunSnapshot sentinelSnapshot = sentinel.snapshot(MemorySnapshotFixtures.calm());
        assertAll(
            () -> assertThat(lines(outBuffer)).isEmpty(),
            () -> assertThat(sentinelSnapshot.tests()).isEqualTo(7));
    }

    @Test
    @DisplayName("sesja, która tylko wykryła testy (np. w IDE), nie daje pustego raportu")
    void session_whenOnlyDiscovered_printsNothing() {

        // given
        LauncherConfig config = config(() -> out);

        // when
        NestedLauncher.discoverOnly(config, MixedOutcomeCases.class);

        // then
        assertThat(lines(outBuffer)).isEmpty();
    }

    @Test
    @DisplayName("plan bez testów, np. Failsafe w module bez testów integracyjnych, nie daje raportu")
    void session_whenPlanHasNoTests_printsNothing() {

        // when
        execute(Map.of(), () -> out, FixtureRun.class);

        // then
        assertThat(lines(outBuffer)).isEmpty();
    }

    @Test
    @DisplayName("błąd przy wypisaniu raportu: jedna linia ostrzeżenia, sesja kończy się normalnie")
    void session_whenReportFails_warnsAndClosesQuietly() {

        // given
        Optional<TestRunRecorder> before = TestRunRecorder.active();
        Supplier<PrintStream> broken = () -> {

            throw new IllegalStateException("stdout gone");
        };

        // when & then
        assertAll(
            () -> assertThatNoException().isThrownBy(() -> execute(Map.of(), broken, MixedOutcomeCases.class)),
            () -> assertThat(lines(errBuffer))
                .containsExactly(P + "raport nie powstał: IllegalStateException: stdout gone"),
            () -> assertThat(TestRunRecorder.active()).isEqualTo(before));
    }

    private void execute(Map<String, String> parameters, Supplier<PrintStream> output, Class<?>... classes) {

        LauncherConfig config = config(output);
        NestedLauncher.execute(config, parameters, classes);
    }

    private LauncherConfig config(Supplier<PrintStream> output) {

        TestDiagnosticsSessionListener session = new TestDiagnosticsSessionListener(System::nanoTime, output, () -> err);
        TestDiagnosticsExecutionListener execution = new TestDiagnosticsExecutionListener();
        return NestedLauncher.isolated()
            .addLauncherSessionListeners(session)
            .addTestExecutionListeners(execution)
            .build();
    }

    private static List<String> lines(ByteArrayOutputStream buffer) {

        String printed = buffer.toString(StandardCharsets.UTF_8);
        return printed.lines().toList();
    }
}
