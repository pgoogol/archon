package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

class HttpRequestBoundaryTest {

    private final List<UnitOfWorkSummary> reported = new ArrayList<>();

    private final DiagnosticsEngine engine = engine();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/7");

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("żądanie dopasowane do trasy daje jednostkę HTTP nazwaną wzorcem, nie adresem")
    void doFilter_whenRouteMatched_namesUnitAfterPattern() throws ServletException, IOException {

        // given
        HttpRequestBoundary boundary = new HttpRequestBoundary(engine, Optional::empty);
        FilterChain handler = (servletRequest, servletResponse) -> {

            servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
            engine.record(DataAccessEventFixtures.read("select * from orders where id = ?"));
        };

        // when
        boundary.doFilter(request, response, handler);

        // then
        UnitOfWorkSummary unit = reported.getFirst();
        assertAll(
            () -> assertThat(reported).hasSize(1),
            () -> assertThat(unit.name()).isEqualTo("GET /orders/{id}"),
            () -> assertThat(unit.type()).isEqualTo(UnitOfWorkType.HTTP),
            () -> assertThat(unit.operationCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("żądanie bez dopasowanej trasy dostaje jedną wspólną nazwę zamiast adresu")
    void doFilter_whenNoRoute_usesUnmatchedName() throws ServletException, IOException {

        // given
        HttpRequestBoundary boundary = new HttpRequestBoundary(engine, Optional::empty);

        // when
        boundary.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        // then
        assertThat(reported).extracting(UnitOfWorkSummary::name).containsExactly("GET [bez trasy]");
    }

    @Test
    @DisplayName("wyjątek z dalszej części łańcucha nie zostawia otwartej jednostki")
    void doFilter_whenChainThrows_stillReportsUnit() {

        // given
        HttpRequestBoundary boundary = new HttpRequestBoundary(engine, Optional::empty);
        FilterChain failing = (servletRequest, servletResponse) -> {

            throw new ServletException("awaria kontrolera");
        };

        // when & then
        assertThatExceptionOfType(ServletException.class).isThrownBy(() -> boundary.doFilter(request, response, failing));
        assertAll(
            () -> assertThat(reported).hasSize(1),
            () -> assertThat(engine.current()).isEmpty());
    }

    @Test
    @DisplayName("identyfikator śladu z dostawcy trafia do jednostki")
    void doFilter_whenTraceIdAvailable_attachesIt() throws ServletException, IOException {

        // given
        HttpRequestBoundary boundary = new HttpRequestBoundary(engine, () -> Optional.of("6e1b9f"));

        // when
        boundary.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        // then
        assertThat(reported.getFirst().traceId()).isEqualTo("6e1b9f");
    }

    @Test
    @DisplayName("żądanie wewnątrz już otwartej jednostki nie zmienia jej nazwy")
    void doFilter_whenUnitAlreadyOpen_keepsOuterName() throws ServletException, IOException {

        // given
        HttpRequestBoundary boundary = new HttpRequestBoundary(engine, Optional::empty);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");

        // when
        try (UnitOfWorkScope ignored = engine.open("ReportJob.run", UnitOfWorkType.SCHEDULED)) {

            boundary.doFilter(request, response, (servletRequest, servletResponse) -> {
            });
        }

        // then
        assertThat(reported).extracting(UnitOfWorkSummary::name).containsExactly("ReportJob.run");
    }

    private DiagnosticsEngine engine() {

        FindingReporter reporter = (unit, findings) -> reported.add(unit.summary());
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);
        return new DiagnosticsEngine(settings, List.of(), List.of(reporter));
    }
}
