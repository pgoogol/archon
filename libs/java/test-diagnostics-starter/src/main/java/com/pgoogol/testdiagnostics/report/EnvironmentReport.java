package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Sekcja środowisk testowych: każdy nowy kontekst Springa z czasem startu, klasą,
 * profilami i liczbą beanów, a pod tabelą wznowienia wstrzymanych kontekstów.
 */
final class EnvironmentReport {

    private static final int MAX_ENVIRONMENTS = 30;

    private static final String ROW = "%3s  %10s  %-44s %-18s %6s";

    void write(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        lines.section("section.environments");
        List<EnvironmentStart> environments = snapshot.environments();
        if (environments.isEmpty()) {

            lines.text("environments.none");
        } else {

            started(snapshot, format, lines);
        }
        if (snapshot.resumeCount() > 0) {

            String resumeTime = format.duration(snapshot.resumeMillis());
            lines.text("environments.resumed", snapshot.resumeCount(), resumeTime);
            lines.note("environments.resumed.note");
        }
        lines.blank();
    }

    private void started(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        List<EnvironmentStart> environments = snapshot.environments();
        String startTime = format.duration(snapshot.environmentStartMillis());
        lines.text("environments.summary", environments.size(), startTime);
        String header = header(format, lines);
        lines.indented(header);
        int shown = Math.min(environments.size(), MAX_ENVIRONMENTS);
        IntStream.range(0, shown)
            .mapToObj(index -> row(index + 1, environments.get(index), format, lines))
            .forEach(lines::indented);
        if (environments.size() > MAX_ENVIRONMENTS) {

            lines.text("more", environments.size() - MAX_ENVIRONMENTS);
        }
        lines.note("environments.note");
    }

    private String header(ReportFormat format, ReportLines lines) {

        String start = column("environments.column.start", 10, format, lines);
        String testClass = column("environments.column.class", 44, format, lines);
        String profiles = column("environments.column.profiles", 18, format, lines);
        String beans = column("environments.column.beans", 6, format, lines);
        return String.format(Locale.ROOT, ROW, "#", start, testClass, profiles, beans);
    }

    private String row(int number, EnvironmentStart environment, ReportFormat format, ReportLines lines) {

        String start = format.duration(environment.durationMillis());
        String beans = Integer.toString(environment.beanCount());
        if (environment.failed()) {

            start = lines.message("environments.failed");
            beans = "-";
        }
        String shortName = format.shortName(environment.testClassName());
        String testClass = format.fit(shortName, 44);
        String joinedProfiles = String.join(",", environment.profiles());
        String profiles = format.fit(joinedProfiles, 18);
        return String.format(Locale.ROOT, ROW, number, start, testClass, profiles, beans);
    }

    private static String column(String key, int width, ReportFormat format, ReportLines lines) {

        String text = lines.message(key);
        return format.fit(text, width);
    }
}
