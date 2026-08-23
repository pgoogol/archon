package com.pgoogol.finance.recurring.schedule;

import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.recurring.domain.RecurringFrequency;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Arytmetyka terminów rachunku cyklicznego. Czysta logika dat — bez Springa,
 * bez bazy, bez zegara systemowego w środku.
 *
 * <p>Cała trudność siedzi w jednym miejscu: <b>dzień miesiąca większy niż jego
 * długość</b>. Rachunek z 31 dnia w lutym nie znika i nie przesuwa się na marzec
 * — wypada ostatniego dnia lutego, czyli 28 albo 29. Naiwne
 * {@code date.plusMonths(1)} po pierwszym takim przesunięciu gubi oryginalny
 * dzień na zawsze: 31 stycznia + miesiąc daje 28 lutego, a stamtąd + miesiąc
 * daje 28 marca zamiast 31.</p>
 *
 * <p>Dlatego terminy liczymy zawsze od kotwicy — pierwszego miesiąca okresu —
 * a dzień dokładamy do każdego wyliczonego miesiąca z osobna.</p>
 */
public class OccurrenceSchedule {

    /**
     * Terminy w oknie {@code [from, to]}, rosnąco.
     *
     * @param dayOfMonth dzień 1..31; większy niż długość miesiąca przesuwa się
     *                   na jego ostatni dzień
     * @param endsOn     ostatni dzień obowiązywania reguły albo {@code null}
     */
    public List<LocalDate> dueDatesBetween(LocalDate startsOn, LocalDate endsOn,
                                           RecurringFrequency frequency, int dayOfMonth,
                                           LocalDate from, LocalDate to) {

        Objects.requireNonNull(startsOn, "startsOn");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        requireValidDayOfMonth(dayOfMonth);

        LocalDate horizon = earlier(to, endsOn);
        List<LocalDate> dates = new ArrayList<>();
        YearMonth anchor = YearMonth.from(startsOn);
        int step = frequency.monthStep();

        // pętla, nie strumień: liczba kroków zależy od horyzontu, a nie od
        // z góry znanego zakresu — Stream.iterate z warunkiem czytałby się gorzej
        // niż to, co robi
        YearMonth month = anchor;
        LocalDate monthStart = month.atDay(1);
        while (!monthStart.isAfter(horizon)) {

            LocalDate due = dayIn(month, dayOfMonth);
            // monthStart przeliczamy na końcu pętli razem z month — inaczej
            // warunek badałby wciąż pierwszy miesiąc i pętla by nie wyszła
            if (!due.isBefore(startsOn) && !due.isBefore(from) && !due.isAfter(horizon)) {

                dates.add(due);
            }
            month = month.plusMonths(step);
            monthStart = month.atDay(1);
        }
        return List.copyOf(dates);
    }

    /**
     * Dzień w miesiącu, przycięty do jego długości. To jedyne miejsce w module,
     * w którym 31 zamienia się na 28, 29 albo 30.
     */
    public LocalDate dayIn(YearMonth month, int dayOfMonth) {

        Objects.requireNonNull(month, "month");
        requireValidDayOfMonth(dayOfMonth);
        int day = Math.min(dayOfMonth, month.lengthOfMonth());
        return month.atDay(day);
    }

    private LocalDate earlier(LocalDate to, LocalDate endsOn) {

        if (Objects.isNull(endsOn)) {

            return to;
        }
        if (endsOn.isBefore(to)) {

            return endsOn;
        }
        return to;
    }

    private void requireValidDayOfMonth(int dayOfMonth) {

        if (dayOfMonth < 1 || dayOfMonth > 31) {

            throw new IllegalArgumentException(
                ExceptionMessageConstants.DAY_OF_MONTH_OUT_OF_RANGE.formatted(dayOfMonth));
        }
    }
}
