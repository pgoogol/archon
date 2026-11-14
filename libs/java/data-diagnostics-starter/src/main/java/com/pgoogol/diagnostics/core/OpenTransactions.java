package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Stos otwartych transakcji bieżącego wątku. Transakcja jest związana z wątkiem, więc stos
 * też; operacja liczy się do transakcji z wierzchu stosu, czyli do tej, w której faktycznie
 * się wykonała ({@code REQUIRES_NEW} zawiesza zewnętrzną).
 */
final class OpenTransactions {

    private final ThreadLocal<Deque<Open>> stack = ThreadLocal.withInitial(ArrayDeque::new);

    void begin(String name, boolean readOnly, Instant start) {

        Deque<Open> open = stack.get();
        Open transaction = new Open(name, readOnly, start, System.nanoTime(), open.size());
        open.push(transaction);
    }

    void count(OperationKind kind) {

        Open current = stack.get().peek();
        if (Objects.nonNull(current)) {

            current.count(kind);
        }
    }

    /** Zdejmuje transakcję z wierzchu; puste, gdy zakończenie przyszło bez rozpoczęcia. */
    Optional<TransactionEvent> end(TransactionOutcome outcome) {

        Deque<Open> open = stack.get();
        Open finished = open.poll();
        if (open.isEmpty()) {

            stack.remove();
        }
        return Optional.ofNullable(finished)
            .map(transaction -> transaction.toEvent(outcome));
    }

    private static final class Open {

        private final String name;

        private final boolean readOnly;

        private final Instant start;

        private final long startedNanos;

        private final int depth;

        private long reads;

        private long writes;

        private long others;

        private Open(String name, boolean readOnly, Instant start, long startedNanos, int depth) {

            this.name = name;
            this.readOnly = readOnly;
            this.start = start;
            this.startedNanos = startedNanos;
            this.depth = depth;
        }

        private void count(OperationKind kind) {

            switch (kind) {

                case READ -> reads++;
                case WRITE -> writes++;
                case OTHER -> others++;
            }
        }

        private TransactionEvent toEvent(TransactionOutcome outcome) {

            Duration duration = Duration.ofNanos(System.nanoTime() - startedNanos);
            return new TransactionEvent(name, start, duration, outcome, readOnly, depth, reads, writes, others);
        }
    }
}
