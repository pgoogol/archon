package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import java.util.List;

/**
 * Wykrywa, kiedy klasa testów uruchamia nowe środowisko (kontekst Springa), i mierzy
 * jego start. Kontekst z pamięci podręcznej też bywa kosztowny: Spring 7 wstrzymuje
 * nieużywane konteksty i przy powrocie restartuje ich komponenty, więc słuchacz mierzy
 * również takie wznowienie.
 *
 * <p>Kontekst ładuje się tu wcześniej niż zwykle, w {@code beforeTestClass} zamiast
 * w przygotowaniu instancji testu. Kolejność {@value #ORDER} stawia słuchacza po
 * {@code DirtiesContextBeforeModesTestExecutionListener} (1500), żeby kontekst do
 * wyrzucenia przez {@code @DirtiesContext(BEFORE_CLASS)} nie ładował się dwa razy, i
 * przed {@code DependencyInjectionTestExecutionListener} (2000), który i tak by go
 * załadował.</p>
 *
 * <p>Bez aktywnego przebiegu (starter wyłączony albo test poza JUnit Platform)
 * słuchacz nic nie robi.</p>
 */
public class EnvironmentStartListener implements TestExecutionListener, Ordered {

    public static final int ORDER = 1600;

    /**
     * Trafienie w pamięć podręczną bez restartu trwa ułamek milisekundy; dłuższe to
     * wznowienie wstrzymanego kontekstu razem z wstrzymaniem poprzedniego.
     */
    private static final long RESUME_THRESHOLD_MILLIS = 1;

    private static final long NANOS_PER_MILLI = 1_000_000L;

    @Override
    public int getOrder() {

        return ORDER;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {

        TestRunRecorder.active().ifPresent(recorder -> measure(recorder, testContext));
    }

    private void measure(TestRunRecorder recorder, TestContext testContext) {

        if (testContext.hasApplicationContext()) {

            resume(recorder, testContext);
            return;
        }
        start(recorder, testContext);
    }

    private void start(TestRunRecorder recorder, TestContext testContext) {

        String testClass = testContext.getTestClass().getName();
        long startedAt = System.nanoTime();
        try {

            ApplicationContext context = testContext.getApplicationContext();
            long millis = elapsedMillis(startedAt);
            Environment environment = context.getEnvironment();
            List<String> profiles = List.of(environment.getActiveProfiles());
            EnvironmentStart start = EnvironmentStart.started(testClass, millis, profiles, context.getBeanDefinitionCount());
            recorder.environmentStarted(start);
        } catch (RuntimeException | Error failure) {

            long millis = elapsedMillis(startedAt);
            EnvironmentStart failed = EnvironmentStart.failed(testClass, millis);
            recorder.environmentStarted(failed);
            throw failure;
        }
    }

    private void resume(TestRunRecorder recorder, TestContext testContext) {

        long startedAt = System.nanoTime();
        testContext.getApplicationContext();
        long millis = elapsedMillis(startedAt);
        if (millis >= RESUME_THRESHOLD_MILLIS) {

            String testClass = testContext.getTestClass().getName();
            recorder.environmentResumed(testClass, millis);
        }
    }

    private static long elapsedMillis(long startedAt) {

        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
    }
}
