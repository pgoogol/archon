package com.pgoogol.diagnostics.metrics;

import com.pgoogol.diagnostics.core.DataAccessEventBuilder;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.TransactionOutcome;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.analysis.NPlusOneAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

class DiagnosticsMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("operacje, transakcja, rozmiar jednostki i wniosek trafiają do mierników z tagami o niskiej kardynalności")
    void unit_whenClosed_recordsOperationsTransactionsUnitSizeAndFindings() {

        // given
        DiagnosticsEngine engine = engine(false);

        // when
        runUnit(engine, "GET /orders");

        // then
        Timer reads = registry.get("data.diagnostics.operations")
            .tags("store", "postgresql", "kind", "READ", "outcome", "success").timer();
        Timer failures = registry.get("data.diagnostics.operations").tag("outcome", "failure").timer();
        assertAll(
            () -> assertThat(reads.count()).isEqualTo(3),
            () -> assertThat(failures.count()).isEqualTo(1),
            () -> assertThat(registry.get("data.diagnostics.transactions")
                .tags("outcome", "COMMITTED", "read.only", "false").timer().count()).isEqualTo(1),
            () -> assertThat(registry.get("data.diagnostics.unit.operations")
                .tag("unit.type", "http").summary().totalAmount()).isEqualTo(4.0),
            () -> assertThat(registry.get("data.diagnostics.findings")
                .tags("code", "N_PLUS_ONE", "store", "postgresql", "severity", "WARN", "unit.type", "http")
                .counter().count()).isEqualTo(1.0));
    }

    @Test
    @DisplayName("kształt zapytania ani nazwa jednostki nie są domyślnie tagami")
    void unit_whenClosed_keepsShapeAndUnitNameOutOfTags() {

        // given
        DiagnosticsEngine engine = engine(false);

        // when
        runUnit(engine, "GET /orders");

        // then
        List<String> tagKeys = registry.getMeters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .map(tag -> tag.getKey())
            .distinct()
            .toList();
        assertThat(tagKeys).doesNotContain("shape", "unit.name");
    }

    @Test
    @DisplayName("nazwa jednostki za flagą trafia do tagu, a filtr odrzuca nazwy ponad limit")
    void unit_whenUnitNameTagEnabled_limitsDistinctNames() {

        // given
        MeterFilter limit = MeterFilter.maximumAllowableTags("data.diagnostics", "unit.name", 2, MeterFilter.deny());
        registry.config().meterFilter(limit);
        DiagnosticsEngine engine = engine(true);

        // when
        Stream.of("GET /a", "GET /b", "GET /c", "GET /a").forEach(name -> runUnit(engine, name));

        // then
        List<String> names = registry.find("data.diagnostics.unit.operations").summaries().stream()
            .map(summary -> summary.getId().getTag("unit.name"))
            .toList();
        assertThat(names).containsExactlyInAnyOrder("GET /a", "GET /b");
    }

    @Test
    @DisplayName("aplikacja bez rejestru mierników działa dalej, zapis nic nie robi")
    void unit_whenNoRegistry_doesNothing() {

        // given
        DiagnosticsMeters meters = new DiagnosticsMeters(() -> null);
        List<DiagnosticAnalyzer> analyzers = List.of(new MetricsAnalyzer(meters));
        List<FindingReporter> reporters = List.of(new MicrometerFindingReporter(meters, true));
        DiagnosticsEngine engine = new DiagnosticsEngine(settings(), analyzers, reporters);

        // when & then
        assertThatNoException().isThrownBy(() -> runUnit(engine, "GET /orders"));
    }

    private DiagnosticsEngine engine(boolean unitNameTag) {

        DiagnosticsMeters meters = new DiagnosticsMeters(() -> registry);
        List<DiagnosticAnalyzer> analyzers = List.of(new MetricsAnalyzer(meters),
            new NPlusOneAnalyzer(SeverityScale.of(3), 100));
        List<FindingReporter> reporters = List.of(new MicrometerFindingReporter(meters, unitNameTag));
        return new DiagnosticsEngine(settings(), analyzers, reporters);
    }

    private static DiagnosticsSettings settings() {

        return DiagnosticsSettings.defaults(DiagnosticsMode.PROD);
    }

    /** Jednostka z transakcją, trzema takimi samymi odczytami i jednym nieudanym zapisem. */
    private static void runUnit(DiagnosticsEngine engine, String name) {

        try (UnitOfWorkScope ignored = engine.open(name, UnitOfWorkType.HTTP)) {

            engine.beginTransaction("OrderService.list", false);
            Stream.generate(() -> DataAccessEventBuilder.read("select * from item where order_id = ?").build())
                .limit(3)
                .forEach(engine::record);
            engine.record(DataAccessEventBuilder.write("update orders set status = ?").failed().build());
            engine.endTransaction(TransactionOutcome.COMMITTED);
        }
    }
}
