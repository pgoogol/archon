package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.ShapeSummary;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Sesja analizy powtórzeń: liczy kształty wybranych operacji i daje wniosek o każdym
 * kształcie, który padł co najmniej tyle razy, ile wynosi próg. Wspólna dla N+1
 * (odczyty) i braku batcha (zapisy poza batchem), które różnią się tylko filtrem,
 * kodem i tytułem.
 *
 * <p>Kubełek zbiorczy ponad limit kształtów nie daje wniosku: miesza różne zapytania,
 * więc jego licznik nie mówi nic o powtórzeniu jednego z nich.</p>
 */
final class RepeatedShapeSession implements AnalysisSession {

    private final Predicate<DataAccessEvent> counted;

    private final SeverityScale scale;

    private final String code;

    private final String titleTemplate;

    private final ShapeStats stats;

    /**
     * @param titleTemplate tytuł wniosku z dwoma miejscami na liczby: liczbę wykonań i próg
     */
    RepeatedShapeSession(Predicate<DataAccessEvent> counted, SeverityScale scale, String code,
                         String titleTemplate, int shapeLimit) {

        this.counted = counted;
        this.scale = scale;
        this.code = code;
        this.titleTemplate = titleTemplate;
        this.stats = new ShapeStats(shapeLimit);
    }

    @Override
    public void onEvent(DataAccessEvent event) {

        if (counted.test(event)) {

            stats.add(event);
        }
    }

    /** Wnioski od kształtu powtarzanego najczęściej. */
    @Override
    public List<Finding> findings() {

        return stats.summaries().stream()
            .filter(summary -> !summary.overflow())
            .flatMap(summary -> finding(summary).stream())
            .sorted(Comparator.comparingLong(Finding::measured).reversed())
            .toList();
    }

    private Optional<Finding> finding(ShapeSummary summary) {

        String title = titleTemplate.formatted(summary.count(), scale.threshold());
        List<ShapeSummary> shapes = List.of(summary);
        return scale.grade(summary.count())
            .map(severity -> new Finding(code, severity, title, summary.count(), scale.threshold(), shapes));
    }
}
