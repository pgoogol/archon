package com.pgoogol.diagnostics.it;

import com.pgoogol.diagnostics.core.ActivityCollector;
import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
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

    private final List<ActivityEvent> activities = new CopyOnWriteArrayList<>();

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
        activities.clear();
    }

    /** Analiza, która dopisuje tu zdarzenia inne niż operacje; test rejestruje ją jako bean. */
    public DiagnosticAnalyzer activityCollector() {

        return new ActivityCollector(activities);
    }

    /** Zdarzenia podanego typu od ostatniego {@link #clear()}, w kolejności nadejścia. */
    public <T extends ActivityEvent> List<T> activities(Class<T> type) {

        return activities.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
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
