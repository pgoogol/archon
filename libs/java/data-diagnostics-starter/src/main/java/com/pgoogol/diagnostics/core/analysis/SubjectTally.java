package com.pgoogol.diagnostics.core.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Licznik pomiarów według nazwanego przedmiotu (transakcji, metody repozytorium): liczba,
 * suma i maksimum. Ponad limit nowe przedmioty nie są liczone, żeby pamięć jednostki
 * zostawała ograniczona; znane liczą się dalej.
 *
 * <p>Nie jest bezpieczny wątkowo: silnik woła sesje analiz pod blokadą jednostki.</p>
 */
public final class SubjectTally {

    private final int limit;

    private final Map<String, long[]> bySubject = new LinkedHashMap<>();

    public SubjectTally(int limit) {

        if (limit < 1) {

            throw new IllegalArgumentException("limit przedmiotów musi być dodatni: " + limit);
        }
        this.limit = limit;
    }

    public void add(String subject, long value) {

        long[] stats = bySubject.get(subject);
        if (Objects.isNull(stats)) {

            if (bySubject.size() >= limit) {

                return;
            }
            stats = new long[3];
            bySubject.put(subject, stats);
        }
        stats[0]++;
        stats[1] += value;
        stats[2] = Math.max(stats[2], value);
    }

    /** Podsumowania w kolejności pierwszego pomiaru. */
    public List<Entry> entries() {

        return bySubject.entrySet().stream()
            .map(entry -> new Entry(entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]))
            .toList();
    }

    /**
     * @param subject przedmiot pomiarów
     * @param count   liczba pomiarów
     * @param total   suma pomiarów
     * @param max     największy pomiar
     */
    public record Entry(String subject, long count, long total, long max) {

    }
}
