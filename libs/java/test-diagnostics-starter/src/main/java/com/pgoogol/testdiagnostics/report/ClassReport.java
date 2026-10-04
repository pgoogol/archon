package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.ClassRecord;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;

import java.util.Comparator;
import java.util.Locale;

/**
 * Sekcja najwolniejszych klas testów: czas całej klasy, środowiska i samych testów,
 * liczba testów i podpowiedź, skąd się wziął czas.
 */
final class ClassReport {

    private static final int TOP_CLASSES = 15;

    private static final long SLOW_AVERAGE_TEST_MILLIS = 2_000;

    private static final long SLOW_SETUP_MILLIS = 2_000;

    private static final String ROW = "%9s %9s %9s %6s  %-36s %s";

    void write(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        lines.section("section.classes", TOP_CLASSES);
        String header = header(format, lines);
        lines.indented(header);
        snapshot.classes().stream()
            .sorted(Comparator.comparingLong(ClassRecord::durationMillis).reversed())
            .limit(TOP_CLASSES)
            .map(record -> row(record, format, lines))
            .forEach(lines::indented);
        lines.blank();
    }

    private String header(ReportFormat format, ReportLines lines) {

        String total = column("classes.column.total", 9, format, lines);
        String environment = column("classes.column.environment", 9, format, lines);
        String tests = column("classes.column.tests", 9, format, lines);
        String count = column("classes.column.count", 6, format, lines);
        String className = column("classes.column.class", 36, format, lines);
        String hint = lines.message("classes.column.hint");
        return String.format(Locale.ROOT, ROW, total, environment, tests, count, className, hint);
    }

    private String row(ClassRecord record, ReportFormat format, ReportLines lines) {

        String total = format.duration(record.durationMillis());
        String environment = "-";
        if (record.environmentMillis() > 0) {

            environment = format.duration(record.environmentMillis());
        }
        String tests = format.duration(record.testMillis());
        String shortName = format.shortName(record.className());
        String name = format.fit(shortName, 36);
        String hint = hint(record, lines);
        return String.format(Locale.ROOT, ROW, total, environment, tests, record.tests(), name, hint);
    }

    /** Skąd czas klasy: środowisko, wolne testy albo przygotowanie i sprzątanie; pusta, gdy nic nie odstaje. */
    private String hint(ClassRecord record, ReportLines lines) {

        long duration = record.durationMillis();
        if (record.environmentMillis() > duration / 2) {

            return lines.message("hint.environment");
        }
        if (record.tests() > 0 && record.testMillis() / record.tests() >= SLOW_AVERAGE_TEST_MILLIS) {

            return lines.message("hint.slowTests");
        }
        long other = record.otherMillis();
        if (other > duration / 2 && other >= SLOW_SETUP_MILLIS) {

            return lines.message("hint.setup");
        }
        return "";
    }

    private static String column(String key, int width, ReportFormat format, ReportLines lines) {

        String text = lines.message(key);
        return format.fit(text, width);
    }
}
