package com.pgoogol.diagnostics.core;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Niezmienna migawka jednostki pracy dla wyjść wniosków. Koniec jednostki jest też
 * chwilą wykrycia jej wniosków, bo analizy oceniają jednostkę dopiero po zamknięciu.
 *
 * @param id             krótki identyfikator jednostki
 * @param name           nazwa, np. wzorzec trasy {@code GET /orders/{id}}
 * @param type           rodzaj granicy
 * @param traceId        identyfikator śladu, gdy aplikacja ma tracing
 * @param operationCount wszystkie operacje jednostki
 * @param databaseTime   łączny czas operacji w magazynach danych
 * @param start          chwila otwarcia
 * @param end            chwila zamknięcia; {@code null}, dopóki jednostka trwa
 */
public record UnitOfWorkSummary(
    String id,
    String name,
    UnitOfWorkType type,
    @Nullable String traceId,
    long operationCount,
    Duration databaseTime,
    Instant start,
    @Nullable Instant end) {

    public UnitOfWorkSummary {

        Objects.requireNonNull(id, "identyfikator jednostki jest wymagany");
        Objects.requireNonNull(name, "nazwa jednostki jest wymagana");
        Objects.requireNonNull(type, "typ jednostki jest wymagany");
        Objects.requireNonNull(databaseTime, "czas w bazie jest wymagany");
        Objects.requireNonNull(start, "początek jednostki jest wymagany");
    }

    /** Czas od otwarcia do zamknięcia; pusty, dopóki jednostka trwa. */
    public Optional<Duration> duration() {

        return Optional.ofNullable(end)
            .map(closedAt -> Duration.between(start, closedAt));
    }
}
