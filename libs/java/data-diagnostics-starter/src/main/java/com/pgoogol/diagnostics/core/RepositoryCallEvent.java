package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Jedno wywołanie metody repozytorium.
 *
 * @param repository prosta nazwa interfejsu repozytorium, np. {@code OrderRepository}
 * @param method     nazwa metody
 * @param duration   czas wywołania razem z zapytaniami i mapowaniem wyniku
 * @param success    czy wywołanie zakończyło się bez wyjątku
 */
public record RepositoryCallEvent(String repository, String method, Duration duration, boolean success)
    implements ActivityEvent {

    public RepositoryCallEvent {

        Objects.requireNonNull(repository, "nazwa repozytorium jest wymagana");
        Objects.requireNonNull(method, "nazwa metody repozytorium jest wymagana");
        Objects.requireNonNull(duration, "czas wywołania jest wymagany");
    }

    /** {@code Repozytorium.metoda}, tak jak w miejscu wywołania wniosku o kształcie. */
    public String qualifiedMethod() {

        return repository + "." + method;
    }
}
