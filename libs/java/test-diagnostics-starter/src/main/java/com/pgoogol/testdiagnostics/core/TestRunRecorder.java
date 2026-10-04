package com.pgoogol.testdiagnostics.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Zbiera dane jednego przebiegu testów, zwykle jednego forka Surefire albo Failsafe.
 *
 * <p>Słuchacze JUnit i Springa powstają niezależnie, a JUnit tworzy osobną instancję
 * słuchacza dla każdego interfejsu z {@code META-INF/services}. Nie mają wspólnego
 * kontenera, przez który dostaliby ten sam obiekt, więc bieżący przebieg leży w jednym
 * statycznym polu ({@link #active()}). Cała logika zostaje w instancji, którą testy
 * tworzą wprost, ze sztucznym zegarem.</p>
 *
 * <p>Test trafia do klasy podanej przez słuchacza, nie do „klasy, która właśnie
 * trwa”: przy równoległym wykonaniu JUnit kilka klas trwa naraz. Stan chroni
 * {@link ReentrantLock}, nie {@code synchronized}, które na JDK 21 przypina wątek
 * wirtualny do nośnika.</p>
 */
public final class TestRunRecorder {

    private static final AtomicReference<TestRunRecorder> ACTIVE = new AtomicReference<>();

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final LongSupplier nanoClock;

    private final long startNanos;

    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, ClassState> classes = new LinkedHashMap<>();

    private final Map<String, Long> testStarts = new HashMap<>();

    private final List<TestTiming> testTimings = new ArrayList<>();

    private final List<EnvironmentStart> environments = new ArrayList<>();

    private final List<FailureRecord> failures = new ArrayList<>();

    private int classFailures;

    private int resumeCount;

    private long resumeMillis;

    /** @param nanoClock zegar monotoniczny w nanosekundach; przebieg zaczyna się w chwili utworzenia */
    public TestRunRecorder(LongSupplier nanoClock) {

        this.nanoClock = Objects.requireNonNull(nanoClock, "zegar jest wymagany");
        this.startNanos = nanoClock.getAsLong();
    }

    /** Przebieg ustawiony przez {@link #activate()}; pusty poza sesją testów. */
    public static Optional<TestRunRecorder> active() {

        TestRunRecorder current = ACTIVE.get();
        return Optional.ofNullable(current);
    }

    /**
     * Ustawia ten przebieg jako bieżący. Zamknięcie uchwytu przywraca przebieg aktywny
     * wcześniej, więc zagnieżdżone uruchomienie JUnit (np. w testach tego modułu) nie
     * zabiera wyników zewnętrznemu. Uchwyty zamyka się od ostatniego; zamknięcie
     * przebiegu, który przestał być bieżący, niczego nie zmienia.
     */
    public Activation activate() {

        TestRunRecorder previous = ACTIVE.getAndSet(this);
        return () -> ACTIVE.compareAndSet(this, previous);
    }

    public void classStarted(String className) {

        Objects.requireNonNull(className, "nazwa klasy jest wymagana");
        long now = nanoClock.getAsLong();
        update(() -> classes.computeIfAbsent(className, name -> new ClassState(now)));
    }

    /**
     * Kończy klasę. {@link TestOutcome#FAILED} oznacza błąd całej klasy, np. w
     * {@code @BeforeAll} albo przy starcie środowiska. Przerwanie przez założenie nie
     * jest błędem.
     */
    public void classFinished(String className, TestOutcome outcome, String failureMessage) {

        long now = nanoClock.getAsLong();
        update(() -> finishClass(className, outcome, failureMessage, now));
    }

    public void testStarted(String uniqueId) {

        Objects.requireNonNull(uniqueId, "identyfikator testu jest wymagany");
        long now = nanoClock.getAsLong();
        update(() -> testStarts.put(uniqueId, now));
    }

    public void testFinished(String uniqueId, TestResult result) {

        Objects.requireNonNull(result, "wynik testu jest wymagany");
        long now = nanoClock.getAsLong();
        update(() -> finishTest(uniqueId, result, now));
    }

    /** Testy, które nie wystartowały: {@code @Disabled}, warunek wyłączający, klasa przerwana założeniem. */
    public void testsSkipped(String className, int count) {

        long now = nanoClock.getAsLong();
        update(() -> classState(className, now).addSkipped(count));
    }

    public void environmentStarted(EnvironmentStart start) {

        Objects.requireNonNull(start, "start środowiska jest wymagany");
        update(() -> addEnvironment(start));
    }

    /** Wznowienie wstrzymanego środowiska z pamięci podręcznej; Spring 7 restartuje wtedy jego komponenty. */
    public void environmentResumed(String testClassName, long millis) {

        Objects.requireNonNull(testClassName, "nazwa klasy testów jest wymagana");
        update(() -> addResume(testClassName, millis));
    }

    public TestRunSnapshot snapshot(MemorySnapshot memory) {

        long now = nanoClock.getAsLong();
        return read(() -> snapshotAt(now, memory));
    }

    private void finishClass(String className, TestOutcome outcome, String failureMessage, long now) {

        ClassState state = classes.get(className);
        if (Objects.isNull(state)) {

            return;
        }
        state.finish(now);
        if (Objects.equals(outcome, TestOutcome.FAILED)) {

            FailureRecord failure = FailureRecord.ofWholeClass(className, failureMessage);
            classFailures++;
            failures.add(failure);
        }
    }

    private void finishTest(String uniqueId, TestResult result, long now) {

        Long started = testStarts.remove(uniqueId);
        long startedAt = Objects.requireNonNullElse(started, now);
        long millis = (now - startedAt) / NANOS_PER_MILLI;
        ClassState state = classState(result.className(), now);
        state.addTest(result.outcome(), millis);
        TestTiming timing = new TestTiming(result.className(), result.testName(), millis);
        testTimings.add(timing);
        if (Objects.equals(result.outcome(), TestOutcome.FAILED)) {

            FailureRecord failure = new FailureRecord(result.className(), result.testName(), result.failureMessage());
            failures.add(failure);
        }
    }

    private void addEnvironment(EnvironmentStart start) {

        environments.add(start);
        addEnvironmentTime(start.testClassName(), start.durationMillis());
    }

    private void addResume(String testClassName, long millis) {

        resumeCount++;
        resumeMillis += millis;
        addEnvironmentTime(testClassName, millis);
    }

    /** Środowisko klasy {@code @Nested} wlicza się do klasy najwyższego poziomu, jak jej testy. */
    private void addEnvironmentTime(String testClassName, long millis) {

        String topLevel = topLevelName(testClassName);
        ClassState state = classes.get(topLevel);
        if (Objects.nonNull(state)) {

            state.addEnvironmentTime(millis);
        }
    }

    private ClassState classState(String className, long now) {

        return classes.computeIfAbsent(className, name -> new ClassState(now));
    }

    private TestRunSnapshot snapshotAt(long now, MemorySnapshot memory) {

        List<ClassRecord> records = classes.entrySet().stream()
            .map(entry -> entry.getValue().toRecord(entry.getKey(), now))
            .toList();
        long wallMillis = (now - startNanos) / NANOS_PER_MILLI;
        return new TestRunSnapshot(wallMillis, records, testTimings, environments, resumeCount, resumeMillis,
            failures, classFailures, memory);
    }

    private static String topLevelName(String className) {

        int nested = className.indexOf('$');
        if (nested < 0) {

            return className;
        }
        return className.substring(0, nested);
    }

    private void update(Runnable change) {

        lock.lock();
        try {

            change.run();
        } finally {

            lock.unlock();
        }
    }

    private <T> T read(Supplier<T> query) {

        lock.lock();
        try {

            return query.get();
        } finally {

            lock.unlock();
        }
    }

    /** Uchwyt aktywnego przebiegu; zamknięcie przywraca przebieg aktywny wcześniej. */
    @FunctionalInterface
    public interface Activation extends AutoCloseable {

        @Override
        void close();
    }
}
