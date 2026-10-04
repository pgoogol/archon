package com.pgoogol.diagnostics.core;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Builder zdarzeń do testów analiz. Domyślnie udana operacja PostgreSQL poza batchem,
 * trwająca 2 ms, z pełnym tekstem równym kształtowi i bez miejsca wywołania.
 */
public final class DataAccessEventBuilder {

    private final OperationKind kind;

    private final String shape;

    private DataStore store = DataAccessEventFixtures.POSTGRESQL;

    private @Nullable String text;

    private Duration duration = Duration.ofMillis(2);

    private boolean success = true;

    private int batchSize;

    private @Nullable CallSite callSite;

    private DataAccessEventBuilder(OperationKind kind, String shape) {

        this.kind = kind;
        this.shape = shape;
        this.text = shape;
    }

    public static DataAccessEventBuilder read(String shape) {

        return new DataAccessEventBuilder(OperationKind.READ, shape);
    }

    public static DataAccessEventBuilder write(String shape) {

        return new DataAccessEventBuilder(OperationKind.WRITE, shape);
    }

    public DataAccessEventBuilder store(String name) {

        this.store = new DataStore(name);
        return this;
    }

    public DataAccessEventBuilder text(@Nullable String value) {

        this.text = value;
        return this;
    }

    public DataAccessEventBuilder millis(long value) {

        this.duration = Duration.ofMillis(value);
        return this;
    }

    public DataAccessEventBuilder failed() {

        this.success = false;
        return this;
    }

    public DataAccessEventBuilder batchSize(int value) {

        this.batchSize = value;
        return this;
    }

    public DataAccessEventBuilder calledFrom(String className, String method, int line) {

        this.callSite = new CallSite(className, method, line, null);
        return this;
    }

    public DataAccessEvent build() {

        Instant timestamp = DataAccessEventFixtures.TIMESTAMP;
        return new DataAccessEvent(store, kind, text, List.of(), shape, duration, success, batchSize, callSite,
            timestamp);
    }
}
