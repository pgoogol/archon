package com.pgoogol.testdiagnostics.junit;

import com.pgoogol.testdiagnostics.core.MemoryProbe;
import com.pgoogol.testdiagnostics.core.MemorySnapshot;
import com.pgoogol.testdiagnostics.core.RunSettings;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import com.pgoogol.testdiagnostics.report.ReportMessages;
import com.pgoogol.testdiagnostics.report.TestRunReport;
import org.jspecify.annotations.Nullable;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.PrintStream;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Sesja JUnit to jeden przebieg: otwarcie zaczyna pomiar czasu i pamięci, zamknięcie
 * wypisuje raport. Pod Surefire i Failsafe jedna sesja obejmuje cały fork.
 *
 * <p>Raport powstaje tylko po sesji, która coś wykonała. IDE otwierają osobną sesję
 * na samo wykrywanie testów; bez tego warunku dałaby pusty raport.</p>
 *
 * <p>Właściwość systemowa {@code test.diagnostics.enabled=false} wyłącza starter już
 * przy otwarciu sesji: bez pomiaru i bez nasłuchu GC. To samo ustawienie w
 * {@code junit-platform.properties} JUnit podaje dopiero przy starcie wykonania, więc
 * wtedy przebieg się liczy, ale raport nie powstaje.</p>
 *
 * <p>Błąd raportu nigdy nie przerywa builda: trafia jedną linią na {@code System.err}.
 * Otwarcie i zamknięcie sesji JUnit woła z jednego wątku, więc stan otwartego
 * przebiegu nie potrzebuje synchronizacji.</p>
 */
public class TestDiagnosticsSessionListener implements LauncherSessionListener {

    private final LongSupplier nanoClock;

    private final Supplier<PrintStream> output;

    private final Supplier<PrintStream> errors;

    private @Nullable OpenRun run;

    /** Konstruktor dla {@code ServiceLoader}: zegar systemowy, raport na {@code System.out}. */
    public TestDiagnosticsSessionListener() {

        this(System::nanoTime, () -> System.out, () -> System.err);
    }

    /**
     * @param output strumień raportu pobierany przy wypisaniu, bo Surefire podmienia {@code System.out}
     * @param errors strumień ostrzeżenia, gdy raport nie powstał
     */
    TestDiagnosticsSessionListener(LongSupplier nanoClock, Supplier<PrintStream> output, Supplier<PrintStream> errors) {

        this.nanoClock = Objects.requireNonNull(nanoClock, "zegar jest wymagany");
        this.output = Objects.requireNonNull(output, "wyjście raportu jest wymagane");
        this.errors = Objects.requireNonNull(errors, "wyjście ostrzeżeń jest wymagane");
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {

        if (disabledBySystemProperty()) {

            return;
        }
        TestRunRecorder recorder = new TestRunRecorder(nanoClock);
        MemoryProbe memory = MemoryProbe.start();
        TestRunRecorder.Activation activation = recorder.activate();
        run = new OpenRun(recorder, activation, memory);
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {

        OpenRun closing = run;
        run = null;
        if (Objects.isNull(closing)) {

            return;
        }
        try {

            closing.recorder().settings()
                .filter(RunSettings::enabled)
                .ifPresent(settings -> print(closing, settings));
        } catch (RuntimeException | LinkageError failure) {

            String description = TestDiagnosticsExecutionListener.describe(failure);
            PrintStream err = errors.get();
            err.println("[TEST-DIAGNOSTICS] raport nie powstał: " + description);
        } finally {

            closing.activation().close();
            closing.memory().close();
        }
    }

    private void print(OpenRun closing, RunSettings settings) {

        MemorySnapshot memory = closing.memory().snapshot();
        TestRunSnapshot snapshot = closing.recorder().snapshot(memory);
        ReportMessages messages = ReportMessages.forLanguage(settings.language());
        TestRunReport report = new TestRunReport(messages);
        PrintStream out = output.get();
        report.print(snapshot, settings.label(), out);
    }

    private static boolean disabledBySystemProperty() {

        String value = System.getProperty(RunSettings.ENABLED_KEY, "");
        return "false".equalsIgnoreCase(value.strip());
    }

    /** Przebieg między otwarciem a zamknięciem sesji. */
    private record OpenRun(TestRunRecorder recorder, TestRunRecorder.Activation activation, MemoryProbe memory) {
    }
}
