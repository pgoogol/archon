package com.pgoogol.testdiagnostics.junit;

import com.pgoogol.testdiagnostics.core.RunSettings;
import com.pgoogol.testdiagnostics.core.TestOutcome;
import com.pgoogol.testdiagnostics.core.TestResult;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Przekazuje przebieg wykonania JUnit do aktywnego {@link TestRunRecorder}.
 *
 * <p>Test trafia do klasy najwyższego poziomu wyliczonej z {@link TestPlan} (łańcuch
 * rodziców), a nie do „klasy, która właśnie trwa”: przy równoległym wykonaniu kilka
 * klas trwa naraz. Testy klas {@code @Nested} liczą się w klasie zewnętrznej.</p>
 *
 * <p>Gdy JUnit pomija albo przerywa kontener (klasa z {@code @Disabled}, założenie
 * w {@code @BeforeAll}), nie zgłasza jego testów osobno. Słuchacz dolicza je wtedy
 * jako pominięte z potomków w planie, pilnując, żeby żaden test nie liczył się dwa razy.</p>
 *
 * <p>Bez aktywnego przebiegu (np. sesja wyłączona właściwością) słuchacz nic nie robi.</p>
 */
public class TestDiagnosticsExecutionListener implements TestExecutionListener {

    private static final int MAX_MESSAGE_LENGTH = 160;

    private static final String ELLIPSIS = "...";

    private volatile @Nullable TestPlan testPlan;

    private final Set<String> settledTests = ConcurrentHashMap.newKeySet();

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {

        testPlan = plan;
        settledTests.clear();
        if (!plan.containsTests()) {

            // plan bez testów (np. Failsafe w module bez testów integracyjnych) nie daje raportu
            return;
        }
        RunSettings settings = readSettings(plan.getConfigurationParameters());
        TestRunRecorder.active().ifPresent(recorder -> recorder.configure(settings));
    }

    @Override
    public void executionStarted(TestIdentifier identifier) {

        TestRunRecorder.active().ifPresent(recorder -> started(recorder, identifier));
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {

        TestRunRecorder.active().ifPresent(recorder -> skipped(recorder, identifier));
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {

        TestRunRecorder.active().ifPresent(recorder -> finished(recorder, identifier, result));
    }

    /** Ustawienia z parametrów JUnit; pusta albo biała etykieta daje {@value RunSettings#DEFAULT_LABEL}. */
    static RunSettings readSettings(ConfigurationParameters parameters) {

        String label = parameters.get(RunSettings.LABEL_KEY)
            .map(String::strip)
            .filter(Predicate.not(String::isEmpty))
            .orElse(RunSettings.DEFAULT_LABEL);
        String language = parameters.get(RunSettings.LOCALE_KEY).orElse("");
        boolean enabled = parameters.getBoolean(RunSettings.ENABLED_KEY).orElse(true);
        return new RunSettings(label, language, enabled);
    }

    /** Nazwa wyjątku i pierwsza linia komunikatu, najwyżej 160 znaków. */
    static String describe(Throwable throwable) {

        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        String text = type;
        if (Objects.nonNull(message)) {

            text = type + ": " + message;
        }
        String firstLine = text.lines().findFirst().orElse(text);
        if (firstLine.length() <= MAX_MESSAGE_LENGTH) {

            return firstLine;
        }
        return firstLine.substring(0, MAX_MESSAGE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    private void started(TestRunRecorder recorder, TestIdentifier identifier) {

        if (isTopLevelClass(identifier)) {

            String className = className(identifier);
            recorder.classStarted(className);
        }
        if (identifier.isTest()) {

            recorder.testStarted(identifier.getUniqueId());
        }
    }

    private void skipped(TestRunRecorder recorder, TestIdentifier identifier) {

        String className = className(identifier);
        int skipped = settleUnfinishedTests(identifier);
        if (skipped > 0) {

            recorder.testsSkipped(className, skipped);
        }
    }

    private void finished(TestRunRecorder recorder, TestIdentifier identifier, TestExecutionResult result) {

        String className = className(identifier);
        TestOutcome outcome = outcome(result);
        String message = result.getThrowable().map(TestDiagnosticsExecutionListener::describe).orElse("");
        if (identifier.isTest()) {

            settledTests.add(identifier.getUniqueId());
            TestResult testResult = new TestResult(className, identifier.getDisplayName(), outcome, message);
            recorder.testFinished(identifier.getUniqueId(), testResult);
        }
        if (identifier.isContainer() && Objects.equals(outcome, TestOutcome.ABORTED)) {

            skipped(recorder, identifier);
        }
        if (isTopLevelClass(identifier)) {

            recorder.classFinished(className, outcome, message);
        }
    }

    /**
     * Testy pod identyfikatorem (albo on sam), których JUnit nie zgłosi już jako
     * zakończone. Każdy liczy się raz, nawet gdy najpierw przerwie się klasa
     * {@code @Nested}, a potem klasa zewnętrzna.
     */
    private int settleUnfinishedTests(TestIdentifier identifier) {

        Stream<TestIdentifier> candidates = Stream.of(identifier);
        TestPlan plan = testPlan;
        if (Objects.nonNull(plan)) {

            Stream<TestIdentifier> descendants = plan.getDescendants(identifier).stream();
            candidates = Stream.concat(candidates, descendants);
        }
        return (int) candidates
            .filter(TestIdentifier::isTest)
            .map(TestIdentifier::getUniqueId)
            .filter(settledTests::add)
            .count();
    }

    /** Klasa najwyższego poziomu: najbardziej zewnętrzne źródło klasy w łańcuchu rodziców. */
    private String className(TestIdentifier identifier) {

        List<TestIdentifier> chain = ancestry(identifier);
        Optional<String> outermost = chain.stream()
            .map(TestDiagnosticsExecutionListener::sourceClassName)
            .flatMap(Optional::stream)
            .reduce((inner, outer) -> outer);
        return outermost
            .map(TestDiagnosticsExecutionListener::topLevelName)
            .orElseGet(() -> containerBelowEngine(chain));
    }

    /** Identyfikator i jego przodkowie, od niego do korzenia silnika. */
    private List<TestIdentifier> ancestry(TestIdentifier identifier) {

        TestPlan plan = testPlan;
        if (Objects.isNull(plan)) {

            return List.of(identifier);
        }
        return Stream.iterate(identifier, Objects::nonNull, current -> plan.getParent(current).orElse(null))
            .toList();
    }

    /** Test spoza klas (inny silnik): nazwa kontenera tuż pod korzeniem silnika. */
    private static String containerBelowEngine(List<TestIdentifier> chain) {

        int index = Math.max(0, chain.size() - 2);
        TestIdentifier container = chain.get(index);
        return container.getDisplayName();
    }

    private static boolean isTopLevelClass(TestIdentifier identifier) {

        Optional<TestSource> source = identifier.getSource();
        return identifier.isContainer()
            && source.filter(ClassSource.class::isInstance).isPresent()
            && sourceClassName(identifier).filter(name -> !name.contains("$")).isPresent();
    }

    private static Optional<String> sourceClassName(TestIdentifier identifier) {

        Optional<TestSource> source = identifier.getSource();
        return source.flatMap(TestDiagnosticsExecutionListener::className);
    }

    private static Optional<String> className(TestSource source) {

        if (source instanceof ClassSource classSource) {

            return Optional.of(classSource.getClassName());
        }
        if (source instanceof MethodSource methodSource) {

            return Optional.of(methodSource.getClassName());
        }
        return Optional.empty();
    }

    private static String topLevelName(String className) {

        int nested = className.indexOf('$');
        if (nested < 0) {

            return className;
        }
        return className.substring(0, nested);
    }

    private static TestOutcome outcome(TestExecutionResult result) {

        return switch (result.getStatus()) {

            case SUCCESSFUL -> TestOutcome.PASSED;
            case FAILED -> TestOutcome.FAILED;
            case ABORTED -> TestOutcome.ABORTED;
        };
    }
}
