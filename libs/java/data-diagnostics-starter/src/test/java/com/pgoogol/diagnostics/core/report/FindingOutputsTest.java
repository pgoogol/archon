package com.pgoogol.diagnostics.core.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.analysis.NPlusOneAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Oba wyjścia wniosków w prawdziwym silniku: log i plik JSONL obok siebie. */
class FindingOutputsTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogFindingReporter.LOGGER_NAME);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final FindingFingerprint fingerprint = new FindingFingerprint();

    private final FindingJsonWriter writer = new FindingJsonWriter(fingerprint);

    private final DiagnosticAnalyzer nPlusOne = new NPlusOneAnalyzer(SeverityScale.of(3), 100);

    @TempDir
    private Path directory;

    @BeforeEach
    void captureLog() {

        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLog() {

        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("nieudany zapis pliku JSONL nie zabiera wniosku reporterowi logu")
    void close_whenJsonlWriteFails_logReporterStillReports() {

        // given: ścieżka JSONL wskazuje katalog, więc zapis pliku się nie uda
        FindingReporter brokenFile = new JsonLinesFindingReporter(directory, 1_000_000, writer);
        FindingReporter log = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        DiagnosticsEngine engine = engine(List.of(brokenFile, log));

        // when
        runUnitWithRepeatedRead(engine);

        // then
        assertThat(appender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .singleElement().asString().startsWith("N_PLUS_ONE:");
    }

    @Test
    @DisplayName("log i plik JSONL podają ten sam odcisk, więc da się przejść z jednego do drugiego")
    void close_whenBothOutputsWork_shareFingerprint() throws IOException {

        // given
        Path file = directory.resolve("findings.jsonl");
        FindingReporter jsonl = new JsonLinesFindingReporter(file, 1_000_000, writer);
        FindingReporter log = new LogFindingReporter(DiagnosticsMode.PROD, fingerprint);
        DiagnosticsEngine engine = engine(List.of(jsonl, log));

        // when
        runUnitWithRepeatedRead(engine);

        // then
        String line = Files.readAllLines(file).getFirst();
        String fileFingerprint = JsonMapper.builder().build().readTree(line).get("fingerprint").asString();
        List<KeyValuePair> logFields = appender.list.getFirst().getKeyValuePairs();
        assertThat(logFields)
            .filteredOn(pair -> Objects.equals(pair.key, "dd.fingerprint"))
            .extracting(pair -> pair.value)
            .containsExactly(fileFingerprint);
    }

    private DiagnosticsEngine engine(List<FindingReporter> reporters) {

        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.PROD);
        return new DiagnosticsEngine(settings, List.of(nPlusOne), reporters);
    }

    private static void runUnitWithRepeatedRead(DiagnosticsEngine engine) {

        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            Stream.generate(() -> DataAccessEventFixtures.read("select * from order_item where order_id = ?"))
                .limit(3)
                .forEach(engine::record);
        }
    }
}
