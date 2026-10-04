package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.Share;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Zapis wartości w raporcie: czasy, paski udziału, przycinanie i skracanie nazw klas
 * o pakiet wspólny dla całego przebiegu.
 */
public final class ReportFormat {

    private static final int BAR_WIDTH = 20;

    private static final long MILLIS_PER_SECOND = 1_000;

    private static final long MILLIS_PER_MINUTE = 60_000;

    private static final String ELLIPSIS = "...";

    private static final String EMPTY_CELL = "-";

    private final String packagePrefix;

    /** @param packagePrefix pakiet z kropką na końcu, np. {@code com.example.}; pusty, gdy wspólnego brak */
    public ReportFormat(String packagePrefix) {

        this.packagePrefix = Objects.requireNonNull(packagePrefix, "prefiks pakietu jest wymagany, pusty, gdy go brak");
    }

    /** Format ze wspólnym pakietem wszystkich podanych klas. */
    public static ReportFormat forClasses(Collection<String> classNames) {

        List<String> common = classNames.stream()
            .map(ReportFormat::packageParts)
            .reduce(ReportFormat::commonStart)
            .orElse(List.of());
        if (common.isEmpty()) {

            return new ReportFormat("");
        }
        String packageName = String.join(".", common);
        return new ReportFormat(packageName + ".");
    }

    public String packagePrefix() {

        return packagePrefix;
    }

    /** Nazwa klasy bez wspólnego pakietu. */
    public String shortName(String className) {

        if (!packagePrefix.isEmpty() && className.startsWith(packagePrefix)) {

            return className.substring(packagePrefix.length());
        }
        return className;
    }

    /** {@code 12.3 s} poniżej minuty, {@code 2 min 05 s} od minuty. */
    public String duration(long millis) {

        if (millis < MILLIS_PER_MINUTE) {

            double seconds = (double) millis / MILLIS_PER_SECOND;
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        long minutes = millis / MILLIS_PER_MINUTE;
        long seconds = millis / MILLIS_PER_SECOND % 60;
        return String.format(Locale.ROOT, "%d min %02d s", minutes, seconds);
    }

    /** Pasek z 20 znaków: {@code #} za udział, {@code .} za resztę. */
    public String bar(Share share) {

        int filled = 0;
        if (share.whole() > 0) {

            long rounded = Math.round((double) BAR_WIDTH * share.part() / share.whole());
            filled = Math.clamp(rounded, 0, BAR_WIDTH);
        }
        return "#".repeat(filled) + ".".repeat(BAR_WIDTH - filled);
    }

    /** Tekst przycięty do szerokości od lewej, bo koniec nazwy klasy mówi najwięcej; pusty jako {@code -}. */
    public String fit(String text, int width) {

        if (Objects.isNull(text) || text.isEmpty()) {

            return EMPTY_CELL;
        }
        if (text.length() <= width) {

            return text;
        }
        int keep = width - ELLIPSIS.length();
        return ELLIPSIS + text.substring(text.length() - keep);
    }

    private static List<String> packageParts(String className) {

        List<String> parts = List.of(className.split("\\."));
        return parts.subList(0, parts.size() - 1);
    }

    private static List<String> commonStart(List<String> first, List<String> second) {

        int limit = Math.min(first.size(), second.size());
        long same = IntStream.range(0, limit)
            .takeWhile(index -> Objects.equals(first.get(index), second.get(index)))
            .count();
        return first.subList(0, (int) same);
    }
}
