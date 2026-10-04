package com.pgoogol.diagnostics.core;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Jednostka pracy: żądanie HTTP, zadanie harmonogramu, komunikat — granica, w której
 * analizy liczą operacje i po której zamknięciu powstaje raport.
 *
 * <p>Bezpieczna dla wielu wątków, bo zdarzenia z {@code @Async} trafiają do jednostki
 * rodzica z innego wątku. Liczniki są atomowe, a lista zdarzeń to kolejka bez blokad,
 * więc wątki wirtualne nie przypinają się do nośnika.</p>
 *
 * <p>Lista zdarzeń jest ograniczona: jednostka trzyma pierwsze {@code eventLimit}
 * zdarzeń, a resztę tylko liczy. Limit 0 wyłącza listę — w trybie prod zostają same
 * liczniki w analizach.</p>
 */
public class UnitOfWork {

    private final String id;

    private volatile String name;

    private final UnitOfWorkType type;

    private final Instant start;

    private final @Nullable String traceId;

    private final int eventLimit;

    private final AtomicLong operations = new AtomicLong();

    private final AtomicLong droppedEvents = new AtomicLong();

    private final AtomicLong databaseNanos = new AtomicLong();

    private final Queue<DataAccessEvent> events = new ConcurrentLinkedQueue<>();

    private final AtomicReference<@Nullable Instant> end = new AtomicReference<>();

    /**
     * @param id         krótki identyfikator do łączenia linii logu z jednej jednostki
     * @param name       nazwa, np. wzorzec trasy {@code GET /orders/{id}}
     * @param type       rodzaj granicy
     * @param start      chwila otwarcia
     * @param traceId    identyfikator śladu, gdy aplikacja ma tracing
     * @param eventLimit ile zdarzeń trzymać na liście; 0 wyłącza listę
     */
    public UnitOfWork(String id, String name, UnitOfWorkType type, Instant start, @Nullable String traceId, int eventLimit) {

        this.id = requireNotBlank(id, "identyfikator jednostki jest wymagany");
        this.name = requireNotBlank(name, "nazwa jednostki jest wymagana");
        this.type = Objects.requireNonNull(type, "typ jednostki jest wymagany");
        this.start = Objects.requireNonNull(start, "początek jednostki jest wymagany");
        this.traceId = traceId;
        if (eventLimit < 0) {

            throw new IllegalArgumentException("limit zdarzeń jednostki nie może być ujemny: " + eventLimit);
        }
        this.eventLimit = eventLimit;
    }

    /**
     * Dolicza operację do jednostki.
     *
     * @return {@code false}, gdy jednostka jest już zamknięta i operacja do niej nie należy
     */
    boolean record(DataAccessEvent event) {

        Objects.requireNonNull(event, "zdarzenie jest wymagane");
        if (!isOpen()) {

            return false;
        }
        long ordinal = operations.incrementAndGet();
        Duration duration = event.duration();
        databaseNanos.addAndGet(duration.toNanos());
        keep(event, ordinal);
        return true;
    }

    /**
     * Zmienia nazwę trwającej jednostki. Granica HTTP zna wzorzec trasy dopiero po
     * dopasowaniu handlera, a jednostkę musi otworzyć wcześniej, więc nadaje nazwę na końcu.
     * Zamknięta jednostka zachowuje nazwę, pod którą trafiła do raportu.
     */
    public void rename(String newName) {

        String checked = requireNotBlank(newName, "nazwa jednostki jest wymagana");
        if (isOpen()) {

            name = checked;
        }
    }

    /** Zamyka jednostkę; kolejne zamknięcie nie zmienia już czasu końca. */
    void close(Instant closedAt) {

        Objects.requireNonNull(closedAt, "koniec jednostki jest wymagany");
        end.compareAndSet(null, closedAt);
    }

    public boolean isOpen() {

        return Objects.isNull(end.get());
    }

    public String id() {

        return id;
    }

    public String name() {

        return name;
    }

    public UnitOfWorkType type() {

        return type;
    }

    public Instant start() {

        return start;
    }

    public Optional<String> traceId() {

        return Optional.ofNullable(traceId);
    }

    public Optional<Instant> end() {

        return Optional.ofNullable(end.get());
    }

    /** Czas od otwarcia do zamknięcia; pusty, dopóki jednostka trwa. */
    public Optional<Duration> duration() {

        return end().map(closedAt -> Duration.between(start, closedAt));
    }

    /** Wszystkie operacje jednostki, także te, których lista już nie zmieściła. */
    public long operationCount() {

        return operations.get();
    }

    /** Łączny czas operacji jednostki w magazynach danych. */
    public Duration databaseTime() {

        long nanos = databaseNanos.get();
        return Duration.ofNanos(nanos);
    }

    /**
     * Niezmienna migawka jednostki dla wyjść wniosków: nie trzyma listy zdarzeń, więc
     * można ją przechować albo przekazać dalej bez trzymania całej jednostki.
     */
    public UnitOfWorkSummary summary() {

        Instant closedAt = end.get();
        Duration database = databaseTime();
        return new UnitOfWorkSummary(id, name, type, traceId, operationCount(), database, start, closedAt);
    }

    /** Operacje policzone, ale nietrzymane na liście, bo przekroczyły limit. */
    public long droppedEvents() {

        return droppedEvents.get();
    }

    /** Kopia listy zdarzeń; pusta, gdy limit wynosi 0. */
    public List<DataAccessEvent> events() {

        return List.copyOf(events);
    }

    private void keep(DataAccessEvent event, long ordinal) {

        if (eventLimit == 0) {

            return;
        }
        if (ordinal <= eventLimit) {

            events.add(event);
            return;
        }
        droppedEvents.incrementAndGet();
    }

    private static String requireNotBlank(String value, String message) {

        Objects.requireNonNull(value, message);
        if (value.isBlank()) {

            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
