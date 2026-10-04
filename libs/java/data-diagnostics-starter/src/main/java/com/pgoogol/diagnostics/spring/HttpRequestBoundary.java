package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Objects;

/**
 * Granica HTTP: jedna jednostka pracy na żądanie servletowe.
 *
 * <p>Filtr stoi przed Spring Security ({@link #ORDER}), więc zapytania z uwierzytelniania
 * też liczą się do żądania. Stoi za filtrem obserwacji Micrometer, więc ślad już istnieje.
 * Nazwą jednostki jest wzorzec trasy ({@code GET /orders/{id}}), nie surowy adres, żeby
 * {@code /orders/1} i {@code /orders/2} nie mnożyły nazw. Wzorzec jest znany dopiero po
 * dopasowaniu handlera, więc filtr nadaje nazwę na końcu żądania. Żądanie bez
 * dopasowanej trasy (404, zasoby statyczne) dostaje nazwę {@code GET [bez trasy]}.</p>
 *
 * <p>Asynchroniczne dokończenie żądania ({@code DeferredResult}) leży poza jednostką:
 * zamyka się ona, gdy wątek żądania oddaje odpowiedź kontenerowi.</p>
 */
public class HttpRequestBoundary extends OncePerRequestFilter {

    /** Przed Spring Security ({@code -100}), za obserwacją żądań ({@code HIGHEST_PRECEDENCE + 1}). */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    static final String UNMATCHED_ROUTE = "[bez trasy]";

    private final DiagnosticsEngine engine;

    private final TraceIdProvider traceIds;

    public HttpRequestBoundary(DiagnosticsEngine engine, TraceIdProvider traceIds) {

        this.engine = Objects.requireNonNull(engine, "silnik jest wymagany");
        this.traceIds = Objects.requireNonNull(traceIds, "dostawca śladu jest wymagany");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String traceId = traceIds.currentTraceId().orElse(null);
        String provisionalName = unitName(request);
        try (UnitOfWorkScope scope = engine.open(provisionalName, UnitOfWorkType.HTTP, traceId)) {

            try {

                chain.doFilter(request, response);
            } finally {

                nameAfterRoute(scope, request);
            }
        }
    }

    private void nameAfterRoute(UnitOfWorkScope scope, HttpServletRequest request) {

        if (scope.opened()) {

            String name = unitName(request);
            scope.unit().rename(name);
        }
    }

    private String unitName(HttpServletRequest request) {

        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = Objects.toString(pattern, UNMATCHED_ROUTE);
        return request.getMethod() + " " + route;
    }
}
