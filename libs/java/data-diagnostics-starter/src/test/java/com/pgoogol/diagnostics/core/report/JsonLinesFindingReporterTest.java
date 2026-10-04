package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.pgoogol.diagnostics.core.report.FindingFixtures.END;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.START;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.caller;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shape;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shapeFinding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

class JsonLinesFindingReporterTest {

    private static final Finding N_PLUS_ONE = shapeFinding("N_PLUS_ONE", shape("select 1", caller("list", 42)));

    private static final Finding SLOW = shapeFinding("SLOW_OPERATION", shape("select 2", caller("export", 7)));

    private final FindingJsonWriter writer = new FindingJsonWriter(new FindingFingerprint());

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final UnitOfWork unit = UnitOfWorkFixtures.closedHttpUnit(START, END);

    @TempDir
    private Path directory;

    @Test
    @DisplayName("każdy wniosek daje osobną linię JSON, a kolejne raporty dopisują się na końcu")
    void report_whenCalledTwice_appendsOneLinePerFinding() throws IOException {

        // given
        Path file = directory.resolve("findings.jsonl");
        JsonLinesFindingReporter reporter = reporter(file, JsonLinesFindingReporter.DEFAULT_MAX_BYTES);

        // when
        reporter.report(unit, List.of(N_PLUS_ONE, SLOW));
        reporter.report(unit, List.of(N_PLUS_ONE));

        // then
        List<String> codes = Files.readAllLines(file).stream()
            .map(line -> mapper.readTree(line).get("code").asString())
            .toList();
        assertThat(codes).containsExactly("N_PLUS_ONE", "SLOW_OPERATION", "N_PLUS_ONE");
    }

    @Test
    @DisplayName("jednostka bez wniosków nie tworzy pliku")
    void report_whenNoFindings_writesNothing() {

        // given
        Path file = directory.resolve("findings.jsonl");
        JsonLinesFindingReporter reporter = reporter(file, JsonLinesFindingReporter.DEFAULT_MAX_BYTES);

        // when
        reporter.report(unit, List.of());

        // then
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("brakujące katalogi powstają przy pierwszym zapisie")
    void report_whenParentDirectoryMissing_createsIt() {

        // given
        Path file = directory.resolve("target/data-diagnostics/findings.jsonl");
        JsonLinesFindingReporter reporter = reporter(file, JsonLinesFindingReporter.DEFAULT_MAX_BYTES);

        // when
        reporter.report(unit, List.of(N_PLUS_ONE));

        // then
        assertThat(file).exists();
    }

    @Test
    @DisplayName("po przekroczeniu limitu plik przechodzi na .1, a zapis zaczyna się od pustego pliku")
    void report_whenSizeLimitReached_rotatesToDotOne() throws IOException {

        // given: limit mniejszy niż dwie linie, więc druga linia wymusza rotację
        Path file = directory.resolve("findings.jsonl");
        JsonLinesFindingReporter reporter = reporter(file, 1_000);

        // when
        reporter.report(unit, List.of(N_PLUS_ONE));
        reporter.report(unit, List.of(SLOW));

        // then
        Path rotated = directory.resolve("findings.jsonl.1");
        assertAll(
            () -> assertThat(Files.readAllLines(rotated)).singleElement().asString().contains("N_PLUS_ONE"),
            () -> assertThat(Files.readAllLines(file)).singleElement().asString().contains("SLOW_OPERATION"));
    }

    @Test
    @DisplayName("równoległe raporty z wielu wątków nie przeplatają linii")
    void report_whenManyThreadsReport_keepsEveryLineIntact() throws IOException {

        // given
        Path file = directory.resolve("findings.jsonl");
        JsonLinesFindingReporter reporter = reporter(file, JsonLinesFindingReporter.DEFAULT_MAX_BYTES);

        // when
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            IntStream.range(0, 200).forEach(index -> executor.submit(() -> reporter.report(unit, List.of(N_PLUS_ONE))));
        }

        // then
        List<String> lines = Files.readAllLines(file);
        assertAll(
            () -> assertThat(lines).hasSize(200),
            () -> assertThat(lines).allSatisfy(line -> assertThatNoException().isThrownBy(() -> mapper.readTree(line))));
    }

    @Test
    @DisplayName("nieudany zapis wychodzi jako UncheckedIOException ze ścieżką pliku")
    void report_whenPathIsDirectory_throwsUncheckedIoException() {

        // given: ścieżka wskazuje istniejący katalog, więc zapis pliku musi się nie udać
        JsonLinesFindingReporter reporter = reporter(directory, JsonLinesFindingReporter.DEFAULT_MAX_BYTES);

        // when & then
        assertThatExceptionOfType(UncheckedIOException.class)
            .isThrownBy(() -> reporter.report(unit, List.of(N_PLUS_ONE)))
            .withMessageContaining(directory.toString());
    }

    private JsonLinesFindingReporter reporter(Path file, long maxBytes) {

        return new JsonLinesFindingReporter(file, maxBytes, writer);
    }
}
