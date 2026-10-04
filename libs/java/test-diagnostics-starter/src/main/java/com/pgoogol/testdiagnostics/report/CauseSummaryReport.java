package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.AttributeDifference;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sekcja najczęstszych przyczyn nowych środowisk: ile środowisk wystartowało przez
 * różnicę w danym atrybucie konfiguracji, a ile przez {@code @DirtiesContext} albo limit
 * pamięci podręcznej. Pierwsze środowisko nie ma przyczyny do usunięcia, więc przebieg
 * z jednym środowiskiem sekcji nie dostaje.
 */
final class CauseSummaryReport {

    void write(TestRunSnapshot snapshot, ReportLines lines) {

        Map<String, Long> counts = snapshot.environments().stream()
            .map(EnvironmentStart::cause)
            .flatMap(CauseSummaryReport::reasons)
            .collect(Collectors.groupingBy(reason -> reason, LinkedHashMap::new, Collectors.counting()));
        if (counts.isEmpty()) {

            return;
        }
        lines.section("section.causes");
        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .forEach(entry -> row(entry.getKey(), entry.getValue(), lines));
        lines.note("causes.note");
        lines.blank();
    }

    /** Przyczyny jednego środowiska: atrybut każdej różnicy albo sposób, w jaki wypadło poprzednie. */
    private static Stream<String> reasons(EnvironmentCause cause) {

        return switch (cause) {

            case EnvironmentCause.First ignored -> Stream.empty();
            case EnvironmentCause.Reloaded reloaded -> Stream.of(reloadedReason(reloaded));
            case EnvironmentCause.Differs differs -> differs.differences().stream().map(AttributeDifference::attribute);
            case EnvironmentCause.Unknown ignored -> Stream.of(Reason.UNKNOWN);
        };
    }

    private static String reloadedReason(EnvironmentCause.Reloaded reloaded) {

        if (reloaded.closedByDirtiesContext()) {

            return Reason.DIRTIED;
        }
        return Reason.EVICTED;
    }

    private static void row(String reason, long count, ReportLines lines) {

        String label = label(reason, lines);
        lines.labeledRow(label, Long.toString(count));
    }

    private static String label(String reason, ReportLines lines) {

        List<String> special = List.of(Reason.DIRTIED, Reason.EVICTED, Reason.UNKNOWN);
        if (special.contains(reason)) {

            return lines.message("summary." + reason);
        }
        return lines.attributeLabel(reason);
    }

    /** Przyczyny spoza atrybutów konfiguracji; klucze {@code summary.<nazwa>} w plikach tekstów. */
    private static final class Reason {

        private static final String DIRTIED = "dirtied";

        private static final String EVICTED = "evicted";

        private static final String UNKNOWN = "unknown";
    }
}
