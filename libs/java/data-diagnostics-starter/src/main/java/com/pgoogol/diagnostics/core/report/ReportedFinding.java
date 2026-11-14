package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWorkSummary;

import java.util.Objects;

/**
 * Wniosek razem z jednostką, w której powstał, i z odciskiem: komplet potrzebny, żeby go
 * później pokazać, odfiltrować albo wyeksportować.
 *
 * @param finding     wniosek
 * @param unit        migawka jednostki
 * @param fingerprint odcisk wniosku
 */
public record ReportedFinding(Finding finding, UnitOfWorkSummary unit, String fingerprint) {

    public ReportedFinding {

        Objects.requireNonNull(finding, "wniosek jest wymagany");
        Objects.requireNonNull(unit, "jednostka jest wymagana");
        Objects.requireNonNull(fingerprint, "odcisk jest wymagany");
    }
}
