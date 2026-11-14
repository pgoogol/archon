package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Eksport „do czatu”: wnioski z kontekstem jako gotowy tekst Markdown do wklejenia
 * w rozmowę z człowiekiem albo z modelem. Biblioteka sama nie woła żadnego modelu.
 *
 * <p>Tekst niesie kształt zapytania, nie jego pełną treść ani parametry, więc nadaje się
 * do wklejenia także wtedy, gdy zapytania dotykają danych wrażliwych.</p>
 */
public class FindingChatExporter {

    private static final String HEADER = """
        # Wnioski diagnostyki dostępu do danych

        Poniżej problemy wykryte w działającej aplikacji (Spring Boot, PostgreSQL). \
        Każdy ma kod, wagę, pomiar z progiem, kształt zapytania i miejsce w kodzie. \
        Zaproponuj poprawkę dla każdego.
        """;

    private static final String EMPTY = "Brak wniosków pasujących do filtra.\n";

    private final FindingHints hints;

    public FindingChatExporter(FindingHints hints) {

        this.hints = Objects.requireNonNull(hints, "podpowiedzi są wymagane");
    }

    public String export(List<ReportedFinding> findings) {

        if (findings.isEmpty()) {

            return EMPTY;
        }
        StringBuilder text = new StringBuilder(HEADER);
        findings.forEach(reported -> append(text, reported));
        return text.toString();
    }

    private void append(StringBuilder text, ReportedFinding reported) {

        Finding finding = reported.finding();
        UnitOfWorkSummary unit = reported.unit();
        long unitMillis = unit.duration().map(Duration::toMillis).orElse(0L);
        text.append("\n## ").append(finding.code()).append(" (").append(finding.severity()).append(")\n\n")
            .append(finding.title()).append("\n\n")
            .append("- jednostka: `").append(unit.name()).append("` (").append(unit.type().name())
            .append("), operacji ").append(unit.operationCount()).append(", w bazie ")
            .append(unit.databaseTime().toMillis()).append(" ms z ").append(unitMillis).append(" ms\n")
            .append("- pomiar: ").append(finding.measured()).append(", próg: ").append(finding.threshold()).append('\n')
            .append("- odcisk: `").append(reported.fingerprint()).append("`\n");
        if (Objects.nonNull(finding.subject())) {

            text.append("- dotyczy: `").append(finding.subject()).append("`\n");
        }
        finding.shapes().forEach(shape -> appendShape(text, shape));
        hints.hintFor(finding.code())
            .ifPresent(hint -> text.append("- typowa poprawka: ").append(hint).append('\n'));
    }

    private void appendShape(StringBuilder text, ShapeSummary shape) {

        text.append("- zapytanie (").append(shape.count()).append("×, łącznie ")
            .append(shape.totalTime().toMillis()).append(" ms, najdłużej ")
            .append(shape.maxTime().toMillis()).append(" ms):\n\n```sql\n")
            .append(shape.shape()).append("\n```\n\n");
        shape.callers().forEach(caller -> appendCaller(text, caller));
    }

    private void appendCaller(StringBuilder text, CallSite caller) {

        text.append("- wywołane z `").append(caller.className()).append('.').append(caller.method())
            .append(':').append(caller.line()).append('`');
        if (Objects.nonNull(caller.repositoryMethod())) {

            text.append(" przez `").append(caller.repositoryMethod()).append('`');
        }
        text.append('\n');
    }
}
