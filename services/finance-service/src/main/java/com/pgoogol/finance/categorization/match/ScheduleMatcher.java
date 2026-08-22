package com.pgoogol.finance.categorization.match;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dopasowanie wiersza wyciągu do pozycji terminarza.
 *
 * <p>Warunki muszą być spełnione <b>wszystkie naraz</b> — waluta ta sama, kwota
 * w granicach {@value #AMOUNT_TOLERANCE_PERCENT}% oczekiwanej, termin w oknie
 * {@value #DATE_TOLERANCE_DAYS} dni od daty księgowania, a opis pasujący do
 * wzorca reguły, jeśli reguła go stawia. Poluzowanie choćby jednego warunku
 * zamienia podpowiedź w zgadywanie, a przy rachunkach dwie różne płatności
 * o zbliżonej kwocie w tym samym tygodniu to nie jest rzadkość.</p>
 *
 * <p>Wynik jest zawsze <b>propozycją</b>. Oznaczenie rachunku jako opłaconego
 * bez potwierdzenia byłoby najgorszym rodzajem cichego błędu w tej
 * aplikacji.</p>
 */
public class ScheduleMatcher {

    static final int AMOUNT_TOLERANCE_PERCENT = 10;
    static final int DATE_TOLERANCE_DAYS = 7;

    private final TextMatcher textMatcher;

    public ScheduleMatcher(TextMatcher textMatcher) {

        this.textMatcher = Objects.requireNonNull(textMatcher, "textMatcher");
    }

    /**
     * Najlepsza pozycja terminarza dla wiersza albo pusty wynik.
     *
     * <p>Przy kilku pasujących wygrywa ta o najmniejszej różnicy kwoty, potem
     * o najbliższym terminie, a na końcu o niższym identyfikatorze — kolejność
     * musi być powtarzalna, inaczej ten sam wyciąg dawałby raz jedną
     * podpowiedź, raz inną.</p>
     */
    public Optional<ScheduleCandidate> bestMatch(RowFacts row, List<ScheduleCandidate> candidates) {

        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(candidates, "candidates");
        return candidates.stream()
            .filter(candidate -> matches(row, candidate))
            .min(byCloseness(row));
    }

    public boolean matches(RowFacts row, ScheduleCandidate candidate) {

        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(candidate, "candidate");
        if (!Objects.equals(row.currency(), candidate.currency())) {

            return false;
        }
        if (!amountWithinTolerance(row, candidate)) {

            return false;
        }
        if (dateDistance(row, candidate) > DATE_TOLERANCE_DAYS) {

            return false;
        }
        return textMatches(row, candidate.matchPattern());
    }

    private boolean amountWithinTolerance(RowFacts row, ScheduleCandidate candidate) {

        long expected = candidate.expectedAmountMinor();
        long difference = amountDistance(row, candidate);
        // mnożenie zamiast dzielenia: przy groszach dzielenie ucięłoby resztę
        // i granica 10% wypadałaby raz wyżej, raz niżej
        return difference * 100 <= Math.abs(expected) * AMOUNT_TOLERANCE_PERCENT;
    }

    /**
     * Wzorzec pusty znaczy „reguła nie stawia warunku na tekst" — sam brak
     * wzorca nie może odrzucać dopasowania, ale i nie może go przepuszczać
     * bez pozostałych warunków, które sprawdziliśmy wcześniej.
     */
    private boolean textMatches(RowFacts row, String matchPattern) {

        if (Objects.isNull(matchPattern) || matchPattern.isBlank()) {

            return true;
        }
        if (textMatcher.contains(row.description(), matchPattern)) {

            return true;
        }
        return textMatcher.contains(row.counterparty(), matchPattern);
    }

    private long amountDistance(RowFacts row, ScheduleCandidate candidate) {

        long expected = Math.abs(candidate.expectedAmountMinor());
        long actual = row.absoluteAmountMinor();
        return Math.abs(actual - expected);
    }

    private long dateDistance(RowFacts row, ScheduleCandidate candidate) {

        long days = ChronoUnit.DAYS.between(candidate.dueDate(), row.bookedOn());
        return Math.abs(days);
    }

    private Comparator<ScheduleCandidate> byCloseness(RowFacts row) {

        Comparator<ScheduleCandidate> byAmount =
            Comparator.comparingLong(candidate -> amountDistance(row, candidate));
        Comparator<ScheduleCandidate> byDate =
            Comparator.comparingLong(candidate -> dateDistance(row, candidate));
        return byAmount.thenComparing(byDate)
            .thenComparing(ScheduleCandidate::occurrenceId);
    }
}
