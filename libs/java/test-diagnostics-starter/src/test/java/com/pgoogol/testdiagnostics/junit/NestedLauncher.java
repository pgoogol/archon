package com.pgoogol.testdiagnostics.junit;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureRun;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Zagnieżdżone uruchomienie JUnit dla klas-wzorców. Automatyczna rejestracja
 * słuchaczy jest wyłączona, więc działają tylko te podane w teście, a zewnętrzny
 * przebieg Surefire niczego z tego uruchomienia nie widzi.
 */
final class NestedLauncher {

    private NestedLauncher() {
    }

    /** Konfiguracja bez słuchaczy z {@code META-INF/services}. */
    static LauncherConfig.Builder isolated() {

        return LauncherConfig.builder()
            .enableTestExecutionListenerAutoRegistration(false)
            .enableLauncherSessionListenerAutoRegistration(false)
            .enableLauncherDiscoveryListenerAutoRegistration(false)
            .enablePostDiscoveryFilterAutoRegistration(false);
    }

    static void execute(LauncherConfig config, Map<String, String> parameters, Class<?>... classes) {

        LauncherDiscoveryRequest request = request(parameters, classes);
        try (LauncherSession session = LauncherFactory.openSession(config)) {

            session.getLauncher().execute(request);
        }
    }

    static void discoverOnly(LauncherConfig config, Class<?>... classes) {

        LauncherDiscoveryRequest request = request(Map.of(), classes);
        try (LauncherSession session = LauncherFactory.openSession(config)) {

            session.getLauncher().discover(request);
        }
    }

    private static LauncherDiscoveryRequest request(Map<String, String> parameters, Class<?>... classes) {

        List<DiscoverySelector> selectors = Stream.of(classes)
            .map(DiscoverySelectors::selectClass)
            .map(DiscoverySelector.class::cast)
            .toList();
        return LauncherDiscoveryRequestBuilder.request()
            .selectors(selectors)
            .configurationParameter(FixtureRun.KEY, "true")
            .configurationParameters(parameters)
            .build();
    }
}
