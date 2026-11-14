package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWorkBoundary;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Message;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Granica komunikatu RabbitMQ: jedna jednostka pracy na dostarczenie komunikatu do
 * słuchacza, nazwana kolejką ({@code rabbit orders.created}). To rada w łańcuchu kontenera
 * słuchaczy, a nie {@code MessagePostProcessor}: post-processor widzi tylko odbiór, a rada
 * obejmuje całe wywołanie słuchacza, więc wie też, kiedy jednostkę zamknąć. Stoi na
 * zewnątrz łańcucha, więc ponowienia komunikatu liczą się do tej samej jednostki.
 */
public class RabbitMessageBoundary implements MethodInterceptor, UnitOfWorkBoundary {

    private static final String UNKNOWN_QUEUE = "(kolejka)";

    private final Supplier<DiagnosticsEngine> engine;

    public RabbitMessageBoundary(Supplier<DiagnosticsEngine> engine) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
    }

    @Override
    public UnitOfWorkType type() {

        return UnitOfWorkType.MESSAGE;
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {

        String queue = Arrays.stream(invocation.getArguments())
            .filter(Message.class::isInstance)
            .map(Message.class::cast)
            .map(message -> message.getMessageProperties().getConsumerQueue())
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(UNKNOWN_QUEUE);
        try (UnitOfWorkScope ignored = engine.get().open("rabbit " + queue, type())) {

            return invocation.proceed();
        }
    }
}
