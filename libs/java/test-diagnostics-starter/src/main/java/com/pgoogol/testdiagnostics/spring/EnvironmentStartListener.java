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
 * Wykrywa, kiedy klasa testów uruchamia nowe środowisko (kontekst Springa), mierzy jego
 * start i podaje powód: czym konfiguracja różni się od środowisk już uruchomionych,
 * albo że ta sama konfiguracja wypadła z pamięci podręcznej. Kontekst z pamięci
 * podręcznej też bywa kosztowny: Spring 7 wstrzymuje nieużywane konteksty i przy
 * powrocie restartuje ich komponenty, więc słuchacz mierzy również takie wznowienie.
 *
 * <p>Kontekst ładuje się tu wcześniej niż zwykle, w {@code beforeTestClass}. Kolejność
 * {@value #ORDER} stawia słuchacza po {@code DirtiesContextBeforeModesTestExecutionListener}
 * (1500), żeby kontekst do wyrzucenia przez {@code @DirtiesContext} nie ładował się dwa
 * razy, i przed {@code DependencyInjectionTestExecutionListener} (2000), który i tak by
 * go załadował. Ten sam warunek obowiązuje przed każdą metodą: po
 * {@code @DirtiesContext} na metodzie kolejna metoda dostaje nowy kontekst.</p>
 *
 * <p>Po klasie i po metodzie słuchacz sprawdza, czy kontekst zniknął z pamięci
 * podręcznej. Wywołania „po” idą w odwrotnej kolejności, więc
 * {@code DirtiesContextTestExecutionListener} (3000) zdążył go już usunąć. Gdy później
 * ta sama konfiguracja wystartuje znów, raport wskaże klasę, która ją zamknęła.</p>
 *
 * <p>Bez aktywnego przebiegu (starter wyłączony albo test poza JUnit Platform)
 * słuchacz nic nie robi.</p>
 */
public class EnvironmentStartListener implements TestExecutionListener, Ordered {

    public static final int ORDER = 1600;

    /** Numer środowiska klasy testów, zapamiętany w jej {@link TestContext}. */
    static final String ENVIRONMENT_ATTRIBUTE = EnvironmentStartListener.class.getName() + ".environment";

    /**
     * Trafienie w pamięć podręczną bez restartu trwa ułamek milisekundy; dłuższe to
     * wznowienie wstrzymanego kontekstu razem z wstrzymaniem poprzedniego.
     */
    private static final long RESUME_THRESHOLD_MILLIS = 1;

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final ContextConfigurationDescriber describer = new ContextConfigurationDescriber();

    @Override
    public int getOrder() {

        return ORDER;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {

        TestRunRecorder.active().ifPresent(recorder -> startOrResume(recorder, testContext));
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {

        TestRunRecorder.active().ifPresent(recorder -> startIfMissing(recorder, testContext));
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {

        TestRunRecorder.active().ifPresent(recorder -> startIfMissing(recorder, testContext));
    }

    @Override
    public void afterTestMethod(TestContext testContext) {

        TestRunRecorder.active().ifPresent(recorder -> noteClosed(recorder, testContext));
    }

    @Override
    public void afterTestClass(TestContext testContext) {

        TestRunRecorder.active().ifPresent(recorder -> noteClosed(recorder, testContext));
    }

    private void startOrResume(TestRunRecorder recorder, TestContext testContext) {

        if (testContext.hasApplicationContext()) {

            resume(recorder, testContext);
            return;
        }
        start(recorder, testContext);
    }

    /** {@code @DirtiesContext(BEFORE_METHOD)} usuwa kontekst przed metodą, więc zamknięcie odnotowuje się tu. */
    private void startIfMissing(TestRunRecorder recorder, TestContext testContext) {

        if (!testContext.hasApplicationContext()) {

            noteClosed(recorder, testContext);
            start(recorder, testContext);
        }
    }

    private void start(TestRunRecorder recorder, TestContext testContext) {

        EnvironmentRegistry registry = EnvironmentRegistry.of(recorder);
        Class<?> testClass = testContext.getTestClass();
        String testClassName = testClass.getName();
        Described described = describer.describe(testClass, registry);
        long startedAt = System.nanoTime();
        try {

            ApplicationContext context = testContext.getApplicationContext();
            long millis = elapsedMillis(startedAt);
            Environment environment = context.getEnvironment();
            List<String> profiles = List.of(environment.getActiveProfiles());
            int beans = context.getBeanDefinitionCount();
            int number = registry.record(recorder, described,
                cause -> EnvironmentStart.started(testClassName, millis, profiles, beans, cause), context);
            testContext.setAttribute(ENVIRONMENT_ATTRIBUTE, number);
        } catch (RuntimeException | Error failure) {

            long millis = elapsedMillis(startedAt);
            registry.record(recorder, described, cause -> EnvironmentStart.failed(testClassName, millis, cause), null);
            throw failure;
        }
    }

    private void resume(TestRunRecorder recorder, TestContext testContext) {

        long startedAt = System.nanoTime();
        ApplicationContext context = testContext.getApplicationContext();
        long millis = elapsedMillis(startedAt);
        if (millis >= RESUME_THRESHOLD_MILLIS) {

            String testClass = testContext.getTestClass().getName();
            recorder.environmentResumed(testClass, millis);
        }
        EnvironmentRegistry registry = EnvironmentRegistry.of(recorder);
        registry.numberOf(context).ifPresent(number -> testContext.setAttribute(ENVIRONMENT_ATTRIBUTE, number));
    }

    /** Kontekst klasy zniknął z pamięci podręcznej: zamknął go {@code @DirtiesContext}. */
    private void noteClosed(TestRunRecorder recorder, TestContext testContext) {

        if (!(testContext.getAttribute(ENVIRONMENT_ATTRIBUTE) instanceof Integer number)
            || testContext.hasApplicationContext()) {

            return;
        }
        testContext.removeAttribute(ENVIRONMENT_ATTRIBUTE);
        String testClass = testContext.getTestClass().getName();
        EnvironmentRegistry registry = EnvironmentRegistry.of(recorder);
        registry.closed(number, testClass);
    }

    private static long elapsedMillis(long startedAt) {

        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
    }
}
