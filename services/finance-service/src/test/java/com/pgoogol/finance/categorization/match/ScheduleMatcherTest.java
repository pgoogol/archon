package com.pgoogol.finance.categorization.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dopasowanie wiersza wyciągu do pozycji terminarza — bez kontekstu, bez bazy,
 * na samych liczbach. Tego pilnuje {@code ArchitectureTest}.
 */
class ScheduleMatcherTest {

    private static final LocalDate TERMIN = LocalDate.of(2026, 3, 10);
    private static final long OCZEKIWANA = 10_000L;

    private final ScheduleMatcher matcher = new ScheduleMatcher(new TextMatcher());

    @Test
    @DisplayName("matches gdy kwota jest o 5% wyższa i termin trzy dni wcześniej, daje sugestię")
    void matches_whenAmountIs5PercentHigherAndThreeDaysEarlier_suggests() {

        // given
        RowFacts row = row(TERMIN.minusDays(3), -10_500L, "OPLATA ZA PRAD");

        // when
        boolean matched = matcher.matches(row, candidate(null));

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("matches gdy kwota różni się o 20%, nie daje sugestii")
    void matches_whenAmountDiffersBy20Percent_doesNotSuggest() {

        // given
        RowFacts row = row(TERMIN, -12_000L, "OPLATA ZA PRAD");

        // when
        boolean matched = matcher.matches(row, candidate(null));

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("matches gdy kwota jest dokładnie na granicy 10%, wciąż daje sugestię")
    void matches_whenAmountIsExactlyAtTolerance_stillSuggests() {

        // given: granica jest domknięta — 11 000 to dokładnie 10% więcej
        RowFacts row = row(TERMIN, -11_000L, "PRAD");

        // when
        boolean matched = matcher.matches(row, candidate(null));

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("matches gdy termin minął o osiem dni, nie daje sugestii")
    void matches_whenDueDateIsEightDaysAway_doesNotSuggest() {

        // given
        RowFacts row = row(TERMIN.plusDays(8), -10_000L, "PRAD");

        // when
        boolean matched = matcher.matches(row, candidate(null));

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("matches gdy waluta jest inna, nie daje sugestii")
    void matches_whenCurrencyDiffers_doesNotSuggest() {

        // given
        RowFacts row = new RowFacts(TERMIN, -10_000L, "EUR", "PRAD", null);

        // when
        boolean matched = matcher.matches(row, candidate(null));

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("matches gdy reguła stawia wzorzec, którego opis nie zawiera, nie daje sugestii")
    void matches_whenPatternIsAbsentFromText_doesNotSuggest() {

        // given
        RowFacts row = row(TERMIN, -10_000L, "ZAKUPY SPOZYWCZE");

        // when
        boolean matched = matcher.matches(row, candidate("prad"));

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("matches gdy wzorzec siedzi w polu kontrahenta, daje sugestię")
    void matches_whenPatternIsInCounterparty_suggests() {

        // given
        RowFacts row = new RowFacts(TERMIN, -10_000L, "PLN", "PRZELEW", "Tauron  Sprzedaż");

        // when: wzorzec porównujemy po zwinięciu spacji i bez wielkości liter
        boolean matched = matcher.matches(row, candidate("tauron sprzedaż"));

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("bestMatch gdy pasuje kilka pozycji, wybiera najbliższą kwotą")
    void bestMatch_whenSeveralMatch_picksClosestByAmount() {

        // given
        RowFacts row = row(TERMIN, -10_100L, "PRAD");
        ScheduleCandidate dalsza = new ScheduleCandidate(1L, TERMIN, 10_900L, "PLN", null);
        ScheduleCandidate blizsza = new ScheduleCandidate(2L, TERMIN, 10_000L, "PLN", null);

        // when
        Optional<ScheduleCandidate> best = matcher.bestMatch(row, List.of(dalsza, blizsza));

        // then
        assertThat(best).contains(blizsza);
    }

    @Test
    @DisplayName("bestMatch gdy nic nie pasuje, zwraca pusty wynik")
    void bestMatch_whenNothingMatches_returnsEmpty() {

        // given
        RowFacts row = row(TERMIN, -50_000L, "PRAD");

        // when
        Optional<ScheduleCandidate> best = matcher.bestMatch(row, List.of(candidate(null)));

        // then
        assertThat(best).isEmpty();
    }

    private RowFacts row(LocalDate bookedOn, long amountMinor, String description) {

        return new RowFacts(bookedOn, amountMinor, "PLN", description, null);
    }

    private ScheduleCandidate candidate(String matchPattern) {

        return new ScheduleCandidate(7L, TERMIN, OCZEKIWANA, "PLN", matchPattern);
    }
}
