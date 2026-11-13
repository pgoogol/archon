package com.pgoogol.diagnostics.metrics;

import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import com.pgoogol.diagnostics.core.report.ShapeSummary;
import io.micrometer.core.instrument.Tags;

import java.util.List;
import java.util.Objects;

/**
 * Wnioski i rozmiar jednostki jako mierniki: licznik wniosków według kodu, magazynu, wagi
 * i typu jednostki oraz rozkład liczby operacji na jednostkę.
 *
 * <p>Nazwa jednostki (wzorzec trasy) trafia do tagu tylko za flagą: tras bywa dużo,
 * a każda wartość tagu to osobna seria. Limit wartości pilnuje filtr mierników
 * z autokonfiguracji.</p>
 */
public class MicrometerFindingReporter implements FindingReporter {

    static final String NO_STORE = "none";

    private final DiagnosticsMeters meters;

    private final boolean unitNameTag;

    /**
     * @param meters      zapis mierników
     * @param unitNameTag czy rozkład operacji na jednostkę ma tag z nazwą jednostki
     */
    public MicrometerFindingReporter(DiagnosticsMeters meters, boolean unitNameTag) {

        this.meters = Objects.requireNonNull(meters, "mierniki są wymagane");
        this.unitNameTag = unitNameTag;
    }

    @Override
    public void report(UnitOfWork unit, List<Finding> findings) {

        String unitType = unit.type().name();
        Tags tags = Tags.of("unit.type", unitType);
        if (unitNameTag) {

            tags = tags.and(DiagnosticsMeters.UNIT_NAME_TAG, unit.name());
        }
        meters.unitOperations(tags, unit.operationCount());
        findings.forEach(finding -> meters.finding(finding.code(), storeOf(finding), finding.severity().name(), unitType));
    }

    /** Magazyn wniosku o kształcie; wniosek o jednostce albo transakcji nie ma magazynu. */
    private String storeOf(Finding finding) {

        return finding.shapes().stream()
            .findFirst()
            .map(ShapeSummary::store)
            .map(store -> store.name())
            .orElse(NO_STORE);
    }
}
