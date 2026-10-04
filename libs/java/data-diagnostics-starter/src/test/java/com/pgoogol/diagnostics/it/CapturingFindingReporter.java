package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reporter testowy, którego silnik dostaje obok wbudowanych. Jednostki spoza granic
 * ({@code startup}, {@code background}) trzyma osobno i nie czyści ich między testami,
 * żeby dało się sprawdzić, co zaszło przy starcie aplikacji.
 */
public class CapturingFindingReporter implements FindingReporter {

    private static final Set<UnitOfWorkType> OUTSIDE_UNITS = Set.of(UnitOfWorkType.STARTUP, UnitOfWorkType.BACKGROUND);

    private final List<Report> reports = new CopyOnWriteArrayList<>();

    private final List<UnitOfWorkSummary> outsideUnits = new CopyOnWriteArrayList<>();

    @Override
    public void report(UnitOfWork unit, List<Finding> findings) {

        UnitOfWorkSummary summary = unit.summary();
        if (OUTSIDE_UNITS.contains(summary.type())) {

            outsideUnits.add(summary);
            return;
        }
        Report report = new Report(summary, List.copyOf(findings));
        reports.add(report);
    }

    public void clear() {

        reports.clear();
    }

    public List<Report> reports() {

        return List.copyOf(reports);
    }

    public List<UnitOfWorkSummary> outsideUnits() {

        return List.copyOf(outsideUnits);
    }

    /** Wnioski o danym kodzie ze wszystkich jednostek od ostatniego {@link #clear()}. */
    public List<Finding> findings(String code) {

        return reports.stream()
            .flatMap(report -> report.findings().stream())
            .filter(finding -> Objects.equals(finding.code(), code))
            .toList();
    }

    public record Report(UnitOfWorkSummary unit, List<Finding> findings) {

    }
}
