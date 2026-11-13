package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import com.pgoogol.diagnostics.core.report.Severity;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

class OpenInViewCheckTest {

    private static final DiagnosticsSettings SETTINGS = DiagnosticsSettings.defaults(DiagnosticsMode.PROD);

    private final List<Finding> reported = new ArrayList<>();

    private final List<UnitOfWorkType> unitTypes = new ArrayList<>();

    @Test
    @DisplayName("aplikacja z interceptorem open-in-view dostaje przy starcie jeden wniosek INFO w jednostce startup")
    void onReady_whenInterceptorPresent_reportsOpenInView() {

        // given
        OpenInViewCheck check = new OpenInViewCheck(this::engine, SETTINGS);

        // when
        check.onApplicationEvent(readyEvent(true));

        // then
        assertAll(
            () -> assertThat(reported).extracting(Finding::code).containsExactly("OPEN_IN_VIEW"),
            () -> assertThat(reported.getFirst().severity()).isEqualTo(Severity.INFO),
            () -> assertThat(unitTypes).containsExactly(UnitOfWorkType.STARTUP));
    }

    @Test
    @DisplayName("bez interceptora albo z wyłączonym sprawdzeniem wniosku nie ma")
    void onReady_whenInterceptorMissingOrCheckDisabled_reportsNothing() {

        // given
        OpenInViewCheck enabled = new OpenInViewCheck(this::engine, SETTINGS);
        OpenInViewCheck disabled = new OpenInViewCheck(this::engine, SETTINGS.withAnalyzerEnabled("open-in-view", false));

        // when
        enabled.onApplicationEvent(readyEvent(false));
        disabled.onApplicationEvent(readyEvent(true));

        // then
        assertThat(reported).isEmpty();
    }

    private DiagnosticsEngine engine() {

        FindingReporter reporter = (unit, findings) -> {

            unitTypes.add(unit.type());
            reported.addAll(findings);
        };
        return new DiagnosticsEngine(SETTINGS, List.of(), List.of(reporter));
    }

    private static ApplicationReadyEvent readyEvent(boolean withInterceptor) {

        GenericApplicationContext context = new GenericApplicationContext();
        if (withInterceptor) {

            context.registerBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class));
            context.registerBean(OpenEntityManagerInViewInterceptor.class, OpenEntityManagerInViewInterceptor::new);
        }
        context.refresh();
        return new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO);
    }
}
