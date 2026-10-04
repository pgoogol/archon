package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.ClassRecord;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.FailureRecord;
import com.pgoogol.testdiagnostics.core.MemorySnapshot;
import com.pgoogol.testdiagnostics.core.Share;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;
import com.pgoogol.testdiagnostics.core.TestTiming;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Raport przebiegu testów dla ludzi technicznych i nietechnicznych: wynik, gdzie
 * poszedł czas, środowiska testowe, najwolniejsze klasy i testy, błędy, pamięć,
 * a na końcu linia {@code DATA} do porównywania buildów.
 *
 * <p>Raport idzie na {@link PrintStream}, nie do loggera: to wynik builda w konsoli
 * Mavena, a logger przeszedłby przez konfigurację logowania testowanej aplikacji.</p>
 */
public final class TestRunReport {

    private static final String RULE = "=".repeat(78);

    private static final int TOP_TESTS = 10;

    private static final int MAX_FAILURES = 30;

    private static final long SLOW_TEST_MILLIS = 1_000;

    private static final long MILLIS_PER_SECOND = 1_000;

    private static final String TIME_VALUE = "%-12s %s %3d%%";

    private static final String TEST_ROW = "%9s  %s > %s";

    private static final String FAILURE_ROW = "X %s > %s : %s";

    private static final String DATA_PATTERN = """
        DATA label=%s tests=%d failed=%d skipped=%d class_failures=%d classes=%d wall_s=%d \
        env_count=%d env_s=%d env_reloaded=%d env_resumed=%d env_resume_s=%d test_s=%d other_s=%d \
        heap_max_mb=%d heap_peak_mb=%d heap_live_mb=%d nonheap_mb=%d gc_s=%d full_gc=%d""";

    private final ReportMessages messages;

    private final EnvironmentReport environmentReport = new EnvironmentReport();

    private final ClassReport classReport = new ClassReport();

    private final CauseSummaryReport causeSummaryReport = new CauseSummaryReport();

    private final MemoryReport memoryReport = new MemoryReport();

    public TestRunReport(ReportMessages messages) {

        this.messages = Objects.requireNonNull(messages, "teksty raportu są wymagane");
    }

    /** Wypisuje raport jednym wywołaniem, żeby linie nie przeplotły się z innym wyjściem. */
    public void print(TestRunSnapshot snapshot, String label, PrintStream out) {

        List<String> lines = render(snapshot, label);
        String text = String.join(System.lineSeparator(), lines);
        out.println(text);
        out.flush();
    }

    /**
     * @param label identyfikator przebiegu, np. {@code unit}; nagłówek pokazuje jego
     *              tłumaczenie, linia {@code DATA} sam identyfikator
     */
    public List<String> render(TestRunSnapshot snapshot, String label) {

        List<String> classNames = snapshot.classes().stream().map(ClassRecord::className).toList();
        ReportFormat format = ReportFormat.forClasses(classNames);
        ReportLines lines = new ReportLines(messages);
        header(label, lines);
        result(snapshot, format, lines);
        time(snapshot, format, lines);
        environmentReport.write(snapshot, format, lines);
        causeSummaryReport.write(snapshot, lines);
        classReport.write(snapshot, format, lines);
        slowestTests(snapshot, format, lines);
        failures(snapshot, format, lines);
        memoryReport.write(snapshot, format, lines);
        data(snapshot, label, lines);
        return lines.lines();
    }

    private void header(String label, ReportLines lines) {

        String runLabel = messages.runLabel(label);
        String title = runLabel.toUpperCase(messages.locale());
        lines.raw(RULE);
        lines.section("header.title", title);
        lines.raw(RULE);
    }

    private void result(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        lines.section("section.result");
        int problems = snapshot.problems();
        if (problems == 0) {

            lines.row("result.verdict", "result.verdict.passed");
        } else {

            lines.row("result.verdict", "result.verdict.problems", problems);
        }
        lines.row("result.tests", "result.tests.value",
            snapshot.tests(), snapshot.passed(), snapshot.failed(), snapshot.skipped());
        String classCount = Integer.toString(snapshot.classes().size());
        lines.rowValue("result.classes", classCount);
        String total = format.duration(snapshot.wallMillis());
        lines.rowValue("result.total", total);
        if (!format.packagePrefix().isEmpty()) {

            lines.rowValue("result.package", format.packagePrefix() + "*");
        }
        lines.blank();
    }

    private void time(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        long wall = snapshot.wallMillis();
        lines.section("section.time");
        timeRow("time.environments", snapshot.environmentStartMillis(), wall, format, lines);
        if (snapshot.resumeCount() > 0) {

            timeRow("time.resumes", snapshot.resumeMillis(), wall, format, lines);
        }
        timeRow("time.tests", snapshot.testMillis(), wall, format, lines);
        timeRow("time.other", snapshot.otherMillis(), wall, format, lines);
        lines.note("time.note");
        lines.blank();
    }

    private void timeRow(String labelKey, long millis, long wall, ReportFormat format, ReportLines lines) {

        Share share = new Share(millis, wall);
        String duration = format.duration(millis);
        String bar = format.bar(share);
        String value = String.format(Locale.ROOT, TIME_VALUE, duration, bar, share.percent());
        lines.rowValue(labelKey, value);
    }

    private void slowestTests(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        lines.section("section.tests", TOP_TESTS);
        List<TestTiming> slow = snapshot.testTimings().stream()
            .filter(timing -> timing.millis() >= SLOW_TEST_MILLIS)
            .sorted(Comparator.comparingLong(TestTiming::millis).reversed())
            .limit(TOP_TESTS)
            .toList();
        if (slow.isEmpty()) {

            lines.text("tests.none");
        } else {

            slow.stream().map(timing -> testRow(timing, format)).forEach(lines::indented);
            lines.note("tests.note");
        }
        lines.blank();
    }

    private String testRow(TestTiming timing, ReportFormat format) {

        String duration = format.duration(timing.millis());
        String shortName = format.shortName(timing.className());
        String className = format.fit(shortName, 40);
        String testName = format.fit(timing.testName(), 40);
        return String.format(Locale.ROOT, TEST_ROW, duration, className, testName);
    }

    private void failures(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        List<FailureRecord> failures = snapshot.failures();
        if (failures.isEmpty()) {

            return;
        }
        lines.section("section.failures", failures.size());
        failures.stream()
            .limit(MAX_FAILURES)
            .map(failure -> failureRow(failure, format))
            .forEach(lines::indented);
        if (failures.size() > MAX_FAILURES) {

            lines.text("more", failures.size() - MAX_FAILURES);
        }
        lines.blank();
    }

    private String failureRow(FailureRecord failure, ReportFormat format) {

        String className = format.shortName(failure.className());
        String testName = failure.testName();
        if (failure.wholeClass()) {

            testName = messages.text("failures.wholeClass");
        }
        return String.format(Locale.ROOT, FAILURE_ROW, className, testName, failure.message());
    }

    private void data(TestRunSnapshot snapshot, String label, ReportLines lines) {

        MemorySnapshot memory = snapshot.memory();
        String dataLabel = label.replace(' ', '_');
        List<ClassRecord> classes = snapshot.classes();
        List<EnvironmentStart> environments = snapshot.environments();
        long keptAfterGc = memory.keptAfterGcMb().orElse(-1);
        String data = String.format(Locale.ROOT, DATA_PATTERN,
            dataLabel, snapshot.tests(), snapshot.failed(), snapshot.skipped(), snapshot.classFailures(),
            classes.size(), seconds(snapshot.wallMillis()),
            environments.size(), seconds(snapshot.environmentStartMillis()), reloaded(environments),
            snapshot.resumeCount(), seconds(snapshot.resumeMillis()),
            seconds(snapshot.testMillis()), seconds(snapshot.otherMillis()),
            memory.maxHeapMb(), memory.heapPeakMb(), keptAfterGc,
            memory.nonHeapMb(), seconds(memory.gcMillis()), memory.fullGcCount());
        lines.raw(data);
        lines.raw(RULE);
    }

    /** Środowiska, które wystartowały ponownie z tą samą konfiguracją. */
    private static long reloaded(List<EnvironmentStart> environments) {

        return environments.stream()
            .filter(environment -> environment.cause() instanceof EnvironmentCause.Reloaded)
            .count();
    }

    private static long seconds(long millis) {

        return millis / MILLIS_PER_SECOND;
    }
}
