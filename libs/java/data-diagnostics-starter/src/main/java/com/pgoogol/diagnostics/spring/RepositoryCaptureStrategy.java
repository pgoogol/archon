package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.RepositoryCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult.State;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Strategia przechwytywania repozytoriów Spring Data: każde wywołanie metody repozytorium
 * (JPA, JDBC i każdego innego modułu) trafia do silnika jako zdarzenie z interfejsem,
 * metodą, czasem i wynikiem. Własny błąd zostaje w logu na DEBUG.
 */
public class RepositoryCaptureStrategy implements RepositoryMethodInvocationListener {

    private static final Logger log = LoggerFactory.getLogger(RepositoryCaptureStrategy.class);

    private final Supplier<DiagnosticsEngine> engine;

    public RepositoryCaptureStrategy(Supplier<DiagnosticsEngine> engine) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
    }

    @Override
    public void afterInvocation(RepositoryMethodInvocation invocation) {

        try {

            String repository = invocation.getRepositoryInterface().getSimpleName();
            String method = invocation.getMethod().getName();
            Duration duration = Duration.ofNanos(invocation.getDuration(TimeUnit.NANOSECONDS));
            boolean success = Objects.equals(invocation.getResult().getState(), State.SUCCESS);
            engine.get().recordActivity(new RepositoryCallEvent(repository, method, duration, success));
        } catch (RuntimeException | LinkageError failure) {

            log.debug("Nie udało się przechwycić wywołania repozytorium", failure);
        }
    }
}
