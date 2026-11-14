package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Zakończona transakcja z licznikami operacji, które wykonał w niej jej wątek.
 *
 * @param name     nazwa transakcji, zwykle {@code Klasa.metoda} z {@code @Transactional}
 * @param start    chwila rozpoczęcia
 * @param duration czas od rozpoczęcia do zatwierdzenia albo wycofania
 * @param outcome  zatwierdzona albo wycofana
 * @param readOnly czy była oznaczona jako tylko do odczytu
 * @param depth    ile transakcji tego wątku było otwartych w chwili jej rozpoczęcia;
 *                 0 to transakcja zewnętrzna, 1 i więcej to {@code REQUIRES_NEW} albo
 *                 {@code NESTED} wewnątrz innej
 * @param reads    liczba odczytów
 * @param writes   liczba zapisów
 * @param others   liczba pozostałych operacji
 */
public record TransactionEvent(
    String name,
    Instant start,
    Duration duration,
    TransactionOutcome outcome,
    boolean readOnly,
    int depth,
    long reads,
    long writes,
    long others) implements ActivityEvent {

    public TransactionEvent {

        Objects.requireNonNull(name, "nazwa transakcji jest wymagana");
        Objects.requireNonNull(start, "początek transakcji jest wymagany");
        Objects.requireNonNull(duration, "czas transakcji jest wymagany");
        Objects.requireNonNull(outcome, "wynik transakcji jest wymagany");
    }

    /** Wszystkie operacje transakcji. */
    public long operations() {

        return reads + writes + others;
    }
}
