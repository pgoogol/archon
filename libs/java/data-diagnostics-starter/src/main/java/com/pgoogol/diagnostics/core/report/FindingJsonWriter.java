package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Wniosek jako jedna linia JSON, gotowa do pliku JSONL, narzędzia albo wklejenia do czatu.
 * Własny zapis zamiast Jacksona, żeby rdzeń nie ciągnął biblioteki JSON do aplikacji.
 *
 * <p>Pola: {@code schema} (wersja formatu), {@code code}, {@code fingerprint},
 * {@code severity}, {@code title}, {@code detectedAt} (koniec jednostki), {@code unit},
 * {@code measured}, {@code threshold}. Wniosek o jednym kształcie ma jego dane na
 * najwyższym poziomie ({@code store}, {@code kind}, {@code shape}, {@code sample},
 * {@code count}, {@code failures}, {@code totalMs}, {@code maxMs}, {@code callers}),
 * żeby dało się po nich filtrować bez zagłębiania. Wniosek o kilku kształtach ma je
 * w tablicy {@code shapes}. Pola bez wartości (np. {@code sample} w prod) są pomijane.</p>
 */
public class FindingJsonWriter {

    /** Wersja formatu; zmienia się, gdy narzędzia czytające wnioski muszą się dostosować. */
    public static final String SCHEMA = "data-diagnostics.finding/v1";

    private final FindingFingerprint fingerprint;

    public FindingJsonWriter(FindingFingerprint fingerprint) {

        this.fingerprint = Objects.requireNonNull(fingerprint, "odcisk wniosku jest wymagany");
    }

    public String write(Finding finding, UnitOfWorkSummary unit) {

        String fingerprintValue = fingerprint.of(finding, unit);
        String detectedAt = Objects.toString(unit.end(), null);
        String unitJson = unit(unit);
        JsonObjectWriter json = new JsonObjectWriter()
            .string("schema", SCHEMA)
            .string("code", finding.code())
            .string("fingerprint", fingerprintValue)
            .string("severity", finding.severity().name())
            .string("title", finding.title())
            .string("detectedAt", detectedAt)
            .object("unit", unitJson)
            .number("measured", finding.measured())
            .number("threshold", finding.threshold());
        appendShapes(json, finding.shapes());
        return json.toJson();
    }

    private void appendShapes(JsonObjectWriter json, List<ShapeSummary> shapes) {

        if (shapes.size() == 1) {

            ShapeSummary shape = shapes.getFirst();
            shapeFields(json, shape);
            return;
        }
        List<String> shapesJson = shapes.stream()
            .map(this::shape)
            .toList();
        json.array("shapes", shapesJson);
    }

    private String unit(UnitOfWorkSummary unit) {

        long durationMs = unit.duration()
            .map(Duration::toMillis)
            .orElse(0L);
        return new JsonObjectWriter()
            .string("id", unit.id())
            .string("name", unit.name())
            .string("type", unit.type().name())
            .string("traceId", unit.traceId())
            .number("operations", unit.operationCount())
            .number("databaseMs", unit.databaseTime().toMillis())
            .number("durationMs", durationMs)
            .toJson();
    }

    private String shape(ShapeSummary shape) {

        JsonObjectWriter json = new JsonObjectWriter();
        shapeFields(json, shape);
        return json.toJson();
    }

    private void shapeFields(JsonObjectWriter json, ShapeSummary shape) {

        List<String> callers = shape.callers().stream()
            .map(this::caller)
            .toList();
        json.string("store", shape.store().name())
            .string("kind", shape.kind().name())
            .string("shape", shape.shape())
            .string("sample", shape.sample())
            .number("count", shape.count())
            .number("failures", shape.failures())
            .number("totalMs", shape.totalTime().toMillis())
            .number("maxMs", shape.maxTime().toMillis())
            .array("callers", callers);
    }

    private String caller(CallSite callSite) {

        return new JsonObjectWriter()
            .string("class", callSite.className())
            .string("method", callSite.method())
            .number("line", callSite.line())
            .string("repositoryMethod", callSite.repositoryMethod())
            .toJson();
    }
}
