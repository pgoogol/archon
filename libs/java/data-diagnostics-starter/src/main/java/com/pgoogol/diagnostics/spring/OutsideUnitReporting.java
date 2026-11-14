package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Raportuje wspólną jednostkę zdarzeń spoza granic: po starcie aplikacji (koniec jednostki
 * {@code startup}) i przy jej zamykaniu (ostatnia jednostka {@code background}). Bez
 * {@code diagnostics.capture-outside-unit} silnik nic wtedy nie robi.
 */
public class OutsideUnitReporting implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private final DiagnosticsEngine engine;

    public OutsideUnitReporting(DiagnosticsEngine engine) {

        this.engine = engine;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        engine.flushOutsideUnit();
    }

    @Override
    public void destroy() {

        engine.flushOutsideUnit();
    }
}
