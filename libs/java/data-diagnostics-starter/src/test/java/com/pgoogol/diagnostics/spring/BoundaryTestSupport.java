package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.report.FindingReporter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Silnik bez analiz, który zapamiętuje podsumowanie każdej zamkniętej jednostki. */
final class BoundaryTestSupport {

    private final List<UnitOfWorkSummary> reported = new CopyOnWriteArrayList<>();

    private final DiagnosticsEngine engine = createEngine();

    DiagnosticsEngine engine() {

        return engine;
    }

    List<UnitOfWorkSummary> reported() {

        return List.copyOf(reported);
    }

    /** Opis każdej jednostki jako {@code typ:nazwa:liczba operacji}. */
    List<String> described() {

        return reported.stream()
            .map(unit -> unit.type().name() + ":" + unit.name() + ":" + unit.operationCount())
            .toList();
    }

    private DiagnosticsEngine createEngine() {

        FindingReporter reporter = (unit, findings) -> reported.add(unit.summary());
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);
        return new DiagnosticsEngine(settings, List.of(), List.of(reporter));
    }
}
