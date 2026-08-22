package com.pgoogol.finance.recurring.schedule;

import com.pgoogol.finance.recurring.domain.RecurringFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Arytmetyka terminów bez kontekstu aplikacji, bez bazy i bez zegara — tak jak
 * pilnuje tego {@code ArchitectureTest}.
 */
class OccurrenceScheduleTest {

    private final OccurrenceSchedule schedule = new OccurrenceSchedule();

    @Test
    @DisplayName("dueDatesBetween gdy dzień to 31 w lutym roku zwykłego, daje 28")
    void dueDatesBetween_whenDay31InCommonYearFebruary_yields28() {

        // given
        LocalDate startsOn = LocalDate.of(2026, 1, 31);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.MONTHLY, 31, startsOn, LocalDate.of(2026, 3, 31));

        // then: luty 2026 ma 28 dni, marzec wraca na 31 — dzień z reguły nie ginie
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("dueDatesBetween gdy dzień to 31 w lutym roku przestępnego, daje 29")
    void dueDatesBetween_whenDay31InLeapYearFebruary_yields29() {

        // given
        LocalDate startsOn = LocalDate.of(2028, 1, 31);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.MONTHLY, 31, startsOn, LocalDate.of(2028, 3, 31));

        // then
        assertThat(dates).containsExactly(
            LocalDate.of(2028, 1, 31),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2028, 3, 31));
    }

    @Test
    @DisplayName("dueDatesBetween gdy okres kończy się w środku, ucina generowanie")
    void dueDatesBetween_whenPeriodEndsMidWindow_truncates() {

        // given
        LocalDate startsOn = LocalDate.of(2026, 1, 10);
        LocalDate endsOn = LocalDate.of(2026, 3, 15);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, endsOn,
            RecurringFrequency.MONTHLY, 10, startsOn, LocalDate.of(2026, 12, 31));

        // then: kwiecień jest już poza okresem obowiązywania reguły
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 3, 10));
    }

    @Test
    @DisplayName("dueDatesBetween gdy częstotliwość jest kwartalna, przeskakuje o trzy miesiące")
    void dueDatesBetween_whenQuarterly_stepsByThreeMonths() {

        // given
        LocalDate startsOn = LocalDate.of(2026, 1, 15);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.QUARTERLY, 15, startsOn, LocalDate.of(2026, 12, 31));

        // then
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("dueDatesBetween gdy częstotliwość jest roczna, daje jeden termin na rok")
    void dueDatesBetween_whenYearly_yieldsOneDatePerYear() {

        // given
        LocalDate startsOn = LocalDate.of(2026, 6, 1);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.YEARLY, 1, startsOn, LocalDate.of(2028, 12, 31));

        // then
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2027, 6, 1),
            LocalDate.of(2028, 6, 1));
    }

    @Test
    @DisplayName("dueDatesBetween gdy okno zaczyna się po starcie reguły, pomija wcześniejsze")
    void dueDatesBetween_whenWindowStartsAfterRule_skipsEarlierDates() {

        // given
        LocalDate startsOn = LocalDate.of(2026, 1, 10);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.MONTHLY, 10, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));

        // then
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 4, 10));
    }

    @Test
    @DisplayName("dueDatesBetween gdy pierwszy termin wypada przed startem, pomija go")
    void dueDatesBetween_whenFirstDueDatePrecedesStart_skipsIt() {

        // given: reguła założona 20 stycznia z płatnością 5 dnia miesiąca —
        // 5 stycznia już minął, więc terminarz zaczyna się w lutym
        LocalDate startsOn = LocalDate.of(2026, 1, 20);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.MONTHLY, 5, startsOn, LocalDate.of(2026, 3, 31));

        // then
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 2, 5),
            LocalDate.of(2026, 3, 5));
    }

    @Test
    @DisplayName("dueDatesBetween gdy okno kończy się przed startem reguły, daje pustą listę")
    void dueDatesBetween_whenWindowEndsBeforeRuleStarts_yieldsNothing() {

        // given
        LocalDate startsOn = LocalDate.of(2026, 6, 1);

        // when
        List<LocalDate> dates = schedule.dueDatesBetween(startsOn, null,
            RecurringFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        // then
        assertThat(dates).isEmpty();
    }

    @Test
    @DisplayName("dayIn gdy dzień przekracza długość miesiąca, przycina do ostatniego")
    void dayIn_whenDayExceedsMonthLength_clampsToLastDay() {

        // given
        YearMonth luty = YearMonth.of(2026, 2);

        // when
        LocalDate due = schedule.dayIn(luty, 31);

        // then
        assertThat(due).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("dayIn gdy dzień jest poza zakresem 1..31, odrzuca go")
    void dayIn_whenDayOutOfRange_rejectsIt() {

        // given
        YearMonth styczen = YearMonth.of(2026, 1);

        // when & then
        assertThatThrownBy(() -> schedule.dayIn(styczen, 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32");
    }
}
