package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWorkBoundary;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Granica komunikatu Kafki: jedna jednostka pracy na rekord, nazwana tematem
 * ({@code kafka orders}). Boot podpina jedyny bean {@link RecordInterceptor} do fabryki
 * kontenerów słuchaczy; aplikacja z własnym interceptorem musi tę granicę wywołać sama.
 *
 * <p>Kontener woła {@code intercept} i {@code afterRecord} w tym samym wątku konsumenta,
 * więc otwarta granica czeka między nimi w zmiennej wątku. Słuchacz wsadowy
 * ({@code batchListener}) nie przechodzi przez ten interceptor.</p>
 */
public class KafkaRecordBoundary implements RecordInterceptor<Object, Object>, UnitOfWorkBoundary {

    private final Supplier<DiagnosticsEngine> engine;

    private final ThreadLocal<UnitOfWorkScope> open = new ThreadLocal<>();

    public KafkaRecordBoundary(Supplier<DiagnosticsEngine> engine) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
    }

    @Override
    public UnitOfWorkType type() {

        return UnitOfWorkType.MESSAGE;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {

        closeOpen();
        UnitOfWorkScope scope = engine.get().open("kafka " + record.topic(), type());
        open.set(scope);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {

        closeOpen();
    }

    /** Zamyka też granicę, której kontener nie zamknął, zanim podał następny rekord. */
    private void closeOpen() {

        UnitOfWorkScope scope = open.get();
        if (Objects.nonNull(scope)) {

            open.remove();
            scope.close();
        }
    }
}
