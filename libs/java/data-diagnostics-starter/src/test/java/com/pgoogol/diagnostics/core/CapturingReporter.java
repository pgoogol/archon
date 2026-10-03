package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reporter testowy: zapamiętuje każdy raport w kolejności nadejścia. */
final class CapturingReporter implements FindingReporter {

    record Report(UnitOfWork unit, List<Finding> findings) {

    }

    private final List<Report> reports = new ArrayList<>();

    @Override
    public void report(UnitOfWork unit, List<Finding> findings) {

        Report report = new Report(unit, findings);
        reports.add(report);
    }

    List<Report> reports() {

        return List.copyOf(reports);
    }

    /** Jedyny raport; test pada, gdy było ich więcej albo wcale. */
    Report single() {

        assertThat(reports).hasSize(1);
        return reports.getFirst();
    }
}
