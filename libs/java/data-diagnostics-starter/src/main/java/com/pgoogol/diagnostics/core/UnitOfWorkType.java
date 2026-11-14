package com.pgoogol.diagnostics.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rodzaj jednostki pracy. Otwarty zbiór zamiast enuma: kolejna granica (np. inny broker
 * komunikatów) dodaje własny typ bez zmiany w rdzeniu. Nazwa trafia do tagów metryk
 * ({@code unit.type}), dlatego tylko małe litery, cyfry i myślniki.
 *
 * @param name nazwa typu
 */
public record UnitOfWorkType(String name) {

    // przed stałymi poniżej: inicjalizacja statyczna idzie w kolejności deklaracji,
    // a każda stała przechodzi przez walidację w konstruktorze
    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /** Żądanie HTTP. */
    public static final UnitOfWorkType HTTP = new UnitOfWorkType("http");

    /** Zadanie {@code @Scheduled}. */
    public static final UnitOfWorkType SCHEDULED = new UnitOfWorkType("scheduled");

    /** Metoda {@code @Async}, której rodzic zdążył się zamknąć. */
    public static final UnitOfWorkType ASYNC = new UnitOfWorkType("async");

    /** Jeden komunikat z brokera. */
    public static final UnitOfWorkType MESSAGE = new UnitOfWorkType("message");

    /** Jeden chunk kroku Spring Batch. */
    public static final UnitOfWorkType BATCH_CHUNK = new UnitOfWorkType("batch-chunk");

    /** Operacje poza jakąkolwiek granicą w czasie startu aplikacji. */
    public static final UnitOfWorkType STARTUP = new UnitOfWorkType("startup");

    /** Operacje poza jakąkolwiek granicą po starcie aplikacji. */
    public static final UnitOfWorkType BACKGROUND = new UnitOfWorkType("background");

    public UnitOfWorkType {

        Objects.requireNonNull(name, "nazwa typu jednostki jest wymagana");
        if (!NAME.matcher(name).matches()) {

            String message = "nazwa typu jednostki może mieć tylko małe litery, cyfry i myślniki: '%s'".formatted(name);
            throw new IllegalArgumentException(message);
        }
    }
}
