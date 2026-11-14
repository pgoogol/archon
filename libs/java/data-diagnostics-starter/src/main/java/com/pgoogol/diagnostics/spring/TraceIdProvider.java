package com.pgoogol.diagnostics.spring;

import java.util.Optional;

/** Identyfikator śladu bieżącego żądania, żeby z wniosku dało się przejść do pełnego śladu. */
@FunctionalInterface
public interface TraceIdProvider {

    Optional<String> currentTraceId();
}
