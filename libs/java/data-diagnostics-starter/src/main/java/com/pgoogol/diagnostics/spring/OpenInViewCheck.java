package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sprawdzenie przy starcie: czy działa open-in-view. Boot włącza je domyślnie, a wtedy
 * każde żądanie trzyma sesję Hibernate (i często połączenie z puli) aż do wyrenderowania
 * odpowiedzi, a lazy loading w kontrolerze po cichu daje dodatkowe zapytania. Wniosek
 * {@code OPEN_IN_VIEW} wychodzi raz, w jednostce {@code startup}, z wagą {@code INFO}.
 *
 * <p>Rozpoznajemy je po beanie interceptora, a nie po właściwości: tylko on mówi, co
 * faktycznie działa. Wyłącza się jak analizę: {@code diagnostics.analyzers.open-in-view.enabled=false}.</p>
 */
public class OpenInViewCheck implements ApplicationListener<ApplicationReadyEvent> {

    public static final String ID = "open-in-view";

    public static final String CODE = "OPEN_IN_VIEW";

    private static final String INTERCEPTOR = "org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor";

    private static final String TITLE = """
        Open-in-view jest włączone: sesja Hibernate żyje do końca żądania. \
        Ustaw spring.jpa.open-in-view=false i dociągaj dane w serwisie""";

    private final Supplier<DiagnosticsEngine> engine;

    private final DiagnosticsSettings settings;

    public OpenInViewCheck(Supplier<DiagnosticsEngine> engine, DiagnosticsSettings settings) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
        this.settings = Objects.requireNonNull(settings, "ustawienia są wymagane");
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        if (!settings.analyzerEnabled(ID) || !openInViewActive(event.getApplicationContext())) {

            return;
        }
        Finding finding = new Finding(CODE, Severity.INFO, TITLE, 0, 0, List.of());
        engine.get().reportStandalone("startup", UnitOfWorkType.STARTUP, List.of(finding));
    }

    private boolean openInViewActive(ConfigurableApplicationContext context) {

        ClassLoader classLoader = context.getClassLoader();
        if (!ClassUtils.isPresent(INTERCEPTOR, classLoader)) {

            return false;
        }
        Class<?> interceptor = ClassUtils.resolveClassName(INTERCEPTOR, classLoader);
        return context.getBeanNamesForType(interceptor, false, false).length > 0;
    }
}
