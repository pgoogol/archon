package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.AttributeDifference;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Powód nowego środowiska jako linie pod wierszem tabeli: jedna linia dla pierwszego
 * środowiska, wznowionej konfiguracji i błędu opisu, a przy różnicy nagłówek i linia
 * na każdy atrybut, np. {@code profiles: + test  - local}.
 */
final class CauseText {

    private static final int MAX_VALUE_LENGTH = 60;

    List<String> lines(EnvironmentCause cause, ReportFormat format, ReportLines lines) {

        String label = lines.message("cause.label");
        return switch (cause) {

            case EnvironmentCause.First ignored -> List.of(label + ": " + lines.message("cause.first"));
            case EnvironmentCause.Reloaded reloaded -> List.of(label + ": " + reloaded(reloaded, format, lines));
            case EnvironmentCause.Differs differs -> differs(label, differs, format, lines);
            case EnvironmentCause.Unknown unknown -> List.of(label + ": " + lines.message("cause.unknown", unknown.reason()));
        };
    }

    private static String reloaded(EnvironmentCause.Reloaded reloaded, ReportFormat format, ReportLines lines) {

        if (reloaded.closedByDirtiesContext()) {

            String closedBy = format.shortName(reloaded.closedBy());
            return lines.message("cause.reloaded.dirtied", reloaded.environment(), closedBy);
        }
        return lines.message("cause.reloaded.evicted", reloaded.environment());
    }

    private static List<String> differs(
        String label, EnvironmentCause.Differs differs, ReportFormat format, ReportLines lines) {

        List<String> result = new ArrayList<>();
        result.add(label + ": " + lines.message("cause.differs", differs.environment()));
        differs.differences().stream()
            .map(difference -> "  " + difference(difference, format, lines))
            .forEach(result::add);
        return result;
    }

    private static String difference(AttributeDifference difference, ReportFormat format, ReportLines lines) {

        String attribute = lines.attributeLabel(difference.attribute());
        List<String> parts = Stream.of(
                changes("+ ", difference.added(), format),
                changes("- ", difference.removed(), format))
            .filter(part -> !part.isEmpty())
            .toList();
        return attribute + ": " + String.join("  ", parts);
    }

    private static String changes(String sign, List<String> values, ReportFormat format) {

        if (values.isEmpty()) {

            return "";
        }
        List<String> shown = values.stream()
            .map(format::shortName)
            .map(value -> format.fit(value, MAX_VALUE_LENGTH))
            .toList();
        return sign + String.join(", ", shown);
    }
}
