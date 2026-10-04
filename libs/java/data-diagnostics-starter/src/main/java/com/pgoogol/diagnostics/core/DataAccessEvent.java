package com.pgoogol.diagnostics.core;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Jedna operacja na magazynie danych, tak jak ją zarejestrowała strategia przechwytywania.
 *
 * <p>Analizy grupują zdarzenia po {@code shape}, nie po {@code text}: to samo zapytanie
 * z innymi wartościami ma ten sam kształt, więc pętla wykonująca je 37 razy daje jeden
 * kształt z licznikiem 37.</p>
 *
 * @param store     magazyn, na którym wykonano operację
 * @param kind      odczyt, zapis albo inna
 * @param text       pełny tekst operacji; {@code null} w trybie prod, gdzie zostaje sam kształt
 * @param parameters wartości parametrów jako tekst, każda przycięta; pusta lista, gdy
 *                   parametrów nie zapisujemy (zawsze w prod, w dev domyślnie)
 * @param shape     tekst po normalizacji: literały i parametry zastąpione {@code ?}
 * @param duration  czas wykonania
 * @param success   czy operacja zakończyła się bez błędu
 * @param batchSize liczba poleceń wysłanych w jednym batchu; 0, gdy operacja nie szła w batchu
 * @param callSite  miejsce w kodzie aplikacji; {@code null}, gdy go nie ustalano albo nie
 *                  dało się ustalić
 * @param timestamp chwila zarejestrowania operacji, po jej zakończeniu
 */
public record DataAccessEvent(
    DataStore store,
    OperationKind kind,
    @Nullable String text,
    List<String> parameters,
    String shape,
    Duration duration,
    boolean success,
    int batchSize,
    @Nullable CallSite callSite,
    Instant timestamp) {

    public DataAccessEvent {

        Objects.requireNonNull(store, "magazyn zdarzenia jest wymagany");
        Objects.requireNonNull(kind, "rodzaj operacji jest wymagany");
        Objects.requireNonNull(shape, "kształt operacji jest wymagany");
        Objects.requireNonNull(duration, "czas operacji jest wymagany");
        Objects.requireNonNull(timestamp, "znacznik czasu zdarzenia jest wymagany");
        if (duration.isNegative()) {

            throw new IllegalArgumentException("czas operacji nie może być ujemny: " + duration);
        }
        if (batchSize < 0) {

            throw new IllegalArgumentException("rozmiar batcha nie może być ujemny: " + batchSize);
        }
        parameters = List.copyOf(parameters);
    }
}
