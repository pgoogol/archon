package com.pgoogol.finance.imports.application;

import com.pgoogol.finance.categorization.application.CategorizationService;
import com.pgoogol.finance.categorization.domain.CategoryRule;
import com.pgoogol.finance.categorization.match.RowFacts;
import com.pgoogol.finance.categorization.match.ScheduleCandidate;
import com.pgoogol.finance.categorization.match.ScheduleMatcher;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.imports.domain.ImportRow;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import com.pgoogol.finance.recurring.infrastructure.ScheduledOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Podpowiedzi dla wierszy wgranego wyciągu: kategoria z reguł i pozycja
 * terminarza do rozliczenia.
 *
 * <p>Obie są <b>propozycjami</b>. Nic tu nie zmienia statusu rachunku ani nie
 * zapisuje kategorii na transakcji — to dzieje się dopiero po potwierdzeniu
 * w podglądzie. Automatyczne oznaczenie rachunku jako opłaconego byłoby
 * najgorszym rodzajem cichego błędu w tej aplikacji.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RowSuggestionService {

    /**
     * O tyle dni poza okres wyciągu sięgamy po pozycje terminarza — samo okno
     * dopasowania to ±7 dni, więc rachunek z początku miesiąca zapłacony
     * ostatniego dnia poprzedniego wciąż ma szansę się znaleźć.
     */
    private static final int LOOKAROUND_DAYS = 7;

    private final CategorizationService categorizationService;
    private final ScheduledOccurrenceRepository occurrenceRepository;
    private final ScheduleMatcher scheduleMatcher;

    /**
     * Uzupełnia podpowiedzi dla całej partii naraz. Reguły i pozycje terminarza
     * wczytujemy raz — wyciąg miesięczny ma kilkaset wierszy, więc zapytanie na
     * wiersz byłoby kilkuset zapytaniami.
     */
    public void suggestFor(long accountId, List<ImportRow> rows) {

        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {

            return;
        }
        List<CategoryRule> rules = categorizationService.list(true);
        List<ScheduledOccurrence> pending = pendingFor(accountId, rows);
        List<ScheduleCandidate> candidates = pending.stream().map(this::toCandidate).toList();
        Map<Long, ScheduledOccurrence> occurrencesById = new HashMap<>();
        pending.forEach(occurrence -> occurrencesById.put(occurrence.getId(), occurrence));
        rows.forEach(row -> suggestFor(row, rules, candidates, occurrencesById));
    }

    private void suggestFor(ImportRow row, List<CategoryRule> rules,
                            List<ScheduleCandidate> candidates,
                            Map<Long, ScheduledOccurrence> occurrencesById) {

        RowFacts facts = factsOf(row);
        Optional<Category> category = categorizationService.suggestFor(facts, rules);
        row.suggestCategory(category.orElse(null));

        Optional<ScheduleCandidate> match = scheduleMatcher.bestMatch(facts, candidates);
        if (match.isEmpty()) {

            row.suggestOccurrence(null);
            return;
        }
        long occurrenceId = match.get().occurrenceId();
        row.suggestOccurrence(occurrencesById.get(occurrenceId));
    }

    private RowFacts factsOf(ImportRow row) {

        return new RowFacts(row.getBookedOn(), row.getAmountMinor(), row.getCurrency(),
            row.getDescription(), row.getCounterparty());
    }

    private List<ScheduledOccurrence> pendingFor(long accountId, List<ImportRow> rows) {

        LocalDate earliest = rows.stream().map(ImportRow::getBookedOn).min(LocalDate::compareTo)
            .orElseThrow();
        LocalDate latest = rows.stream().map(ImportRow::getBookedOn).max(LocalDate::compareTo)
            .orElseThrow();
        LocalDate from = earliest.minusDays(LOOKAROUND_DAYS);
        LocalDate to = latest.plusDays(LOOKAROUND_DAYS);
        return occurrenceRepository.findPendingForAccount(accountId, from, to);
    }

    private ScheduleCandidate toCandidate(ScheduledOccurrence occurrence) {

        RecurringRule rule = occurrence.getRule();
        String matchPattern = rule.getMatchPattern();
        return new ScheduleCandidate(occurrence.getId(), occurrence.getDueDate(),
            occurrence.getExpectedAmountMinor(), occurrence.getCurrency(), matchPattern);
    }
}
