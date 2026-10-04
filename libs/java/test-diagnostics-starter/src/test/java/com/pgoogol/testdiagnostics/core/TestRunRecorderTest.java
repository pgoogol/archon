package com.pgoogol.testdiagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestRunRecorderTest {

    private static final String ORDERS = "com.example.OrderApiTest";

    private static final String PAYMENTS = "com.example.PaymentApiTest";

    private final ManualClock clock = new ManualClock();

    private final TestRunRecorder recorder = new TestRunRecorder(clock);

    @Test
    @DisplayName("udany test liczy się w swojej klasie razem z czasem")
    void testFinished_whenPassed_countsTestAndItsTime() {

        // given
        recorder.classStarted(ORDERS);
        recorder.testStarted("t1");
        clock.advanceMillis(30);

        // when
        recorder.testFinished("t1", TestResult.passed(ORDERS, "lists orders"));

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.classes()).singleElement()
                .extracting(ClassRecord::tests, ClassRecord::testMillis, ClassRecord::failed)
                .containsExactly(1, 30L, 0),
            () -> assertThat(snapshot.testTimings()).containsExactly(new TestTiming(ORDERS, "lists orders", 30)),
            () -> assertThat(snapshot.passed()).isEqualTo(1));
    }

    @Test
    @DisplayName("test z błędem trafia do listy błędów z opisem")
    void testFinished_whenFailed_recordsFailureWithMessage() {

        // given
        recorder.classStarted(ORDERS);
        recorder.testStarted("t1");
        TestResult failed = new TestResult(ORDERS, "rejects empty order", TestOutcome.FAILED, "AssertionError: expected 400");

        // when
        recorder.testFinished("t1", failed);

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.failures())
                .containsExactly(new FailureRecord(ORDERS, "rejects empty order", "AssertionError: expected 400")),
            () -> assertThat(snapshot.failed()).isEqualTo(1),
            () -> assertThat(snapshot.problems()).isEqualTo(1));
    }

    @Test
    @DisplayName("test przerwany założeniem liczy się jako pominięty, nie jako błąd")
    void testFinished_whenAborted_countsAsSkippedNotFailure() {

        // given
        recorder.classStarted(ORDERS);
        recorder.testStarted("t1");
        TestResult aborted = new TestResult(ORDERS, "needs docker", TestOutcome.ABORTED, "");

        // when
        recorder.testFinished("t1", aborted);

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.skipped()).isEqualTo(1),
            () -> assertThat(snapshot.failures()).isEmpty(),
            () -> assertThat(snapshot.problems()).isZero());
    }

    @Test
    @DisplayName("przy klasach trwających naraz każdy test trafia do swojej klasy")
    void testFinished_whenClassesInterleave_attributesTestsToTheirOwnClass() {

        // given: równoległe wykonanie, obie klasy wystartowały, zanim skończył się pierwszy test
        recorder.classStarted(ORDERS);
        recorder.classStarted(PAYMENTS);
        recorder.testStarted("orders-1");
        recorder.testStarted("payments-1");
        clock.advanceMillis(10);

        // when
        recorder.testFinished("orders-1", TestResult.passed(ORDERS, "lists orders"));
        recorder.testFinished("payments-1", TestResult.passed(PAYMENTS, "charges card"));

        // then
        assertThat(snapshot().classes())
            .extracting(ClassRecord::className, ClassRecord::tests)
            .containsExactly(tuple(ORDERS, 1), tuple(PAYMENTS, 1));
    }

    @Test
    @DisplayName("pominięte testy wyłączonej klasy liczą się jako testy i jako pominięte")
    void testsSkipped_whenClassDisabled_countsEveryTestAsSkipped() {

        // given
        recorder.classStarted(ORDERS);

        // when
        recorder.testsSkipped(ORDERS, 3);

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.tests()).isEqualTo(3),
            () -> assertThat(snapshot.skipped()).isEqualTo(3),
            () -> assertThat(snapshot.passed()).isZero());
    }

    @Test
    @DisplayName("błąd całej klasy to problem z pustą nazwą testu")
    void classFinished_whenFailed_recordsWholeClassProblem() {

        // given
        recorder.classStarted(ORDERS);

        // when
        recorder.classFinished(ORDERS, TestOutcome.FAILED, "IllegalStateException: Failed to load ApplicationContext");

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.failures()).singleElement().matches(FailureRecord::wholeClass),
            () -> assertThat(snapshot.classFailures()).isEqualTo(1),
            () -> assertThat(snapshot.problems()).isEqualTo(1));
    }

    @Test
    @DisplayName("klasa przerwana założeniem nie jest problemem")
    void classFinished_whenAborted_isNotAProblem() {

        // given
        recorder.classStarted(ORDERS);

        // when
        recorder.classFinished(ORDERS, TestOutcome.ABORTED, "TestAbortedException: Assumption failed");

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.failures()).isEmpty(),
            () -> assertThat(snapshot.problems()).isZero());
    }

    @Test
    @DisplayName("środowisko klasy @Nested wlicza się do klasy najwyższego poziomu")
    void environmentStarted_whenNestedClass_addsTimeToTopLevelClass() {

        // given
        recorder.classStarted(ORDERS);
        EnvironmentStart start = EnvironmentStart.started(ORDERS + "$WhenEmpty", 4_000, List.of("test"), 412);

        // when
        recorder.environmentStarted(start);

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.environments()).containsExactly(start),
            () -> assertThat(snapshot.classes()).singleElement()
                .extracting(ClassRecord::environmentMillis).isEqualTo(4_000L));
    }

    @Test
    @DisplayName("wznowienie środowiska liczy się osobno od startów i dolicza czas klasie")
    void environmentResumed_countsResumeAndAddsTimeToClass() {

        // given
        recorder.classStarted(ORDERS);

        // when
        recorder.environmentResumed(ORDERS, 250);

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.environments()).isEmpty(),
            () -> assertThat(snapshot.resumeCount()).isEqualTo(1),
            () -> assertThat(snapshot.environmentMillis()).isEqualTo(250),
            () -> assertThat(snapshot.classes()).singleElement()
                .extracting(ClassRecord::environmentMillis).isEqualTo(250L));
    }

    @Test
    @DisplayName("klasa bez końca trwa do chwili migawki")
    void snapshot_whenClassNotFinished_measuresUntilSnapshot() {

        // given
        recorder.classStarted(ORDERS);
        clock.advanceMillis(700);

        // when
        TestRunSnapshot snapshot = snapshot();

        // then
        assertThat(snapshot.classes()).singleElement()
            .extracting(ClassRecord::durationMillis).isEqualTo(700L);
    }

    @Test
    @DisplayName("czas przebiegu dzieli się na testy, środowiska i resztę")
    void snapshot_splitsWallTimeIntoTestsEnvironmentsAndOther() {

        // given
        recorder.classStarted(ORDERS);
        clock.advanceMillis(100);
        recorder.environmentStarted(EnvironmentStart.started(ORDERS, 3_000, List.of(), 300));
        recorder.testStarted("t1");
        clock.advanceMillis(500);
        recorder.testFinished("t1", TestResult.passed(ORDERS, "lists orders"));
        clock.advanceMillis(3_400);
        recorder.classFinished(ORDERS, TestOutcome.PASSED, "");

        // when
        TestRunSnapshot snapshot = snapshot();

        // then
        assertAll(
            () -> assertThat(snapshot.wallMillis()).isEqualTo(4_000),
            () -> assertThat(snapshot.testMillis()).isEqualTo(500),
            () -> assertThat(snapshot.environmentMillis()).isEqualTo(3_000),
            () -> assertThat(snapshot.otherMillis()).isEqualTo(500),
            () -> assertThat(snapshot.classes()).singleElement()
                .extracting(ClassRecord::otherMillis).isEqualTo(500L));
    }

    @Test
    @DisplayName("zamknięcie zagnieżdżonego przebiegu przywraca poprzedni")
    void activate_whenNested_restoresPreviousRunOnClose() {

        // given
        Optional<TestRunRecorder> before = TestRunRecorder.active();
        TestRunRecorder nested = new TestRunRecorder(clock);

        // when
        Optional<TestRunRecorder> during;
        try (TestRunRecorder.Activation ignored = nested.activate()) {

            during = TestRunRecorder.active();
        }

        // then
        assertAll(
            () -> assertThat(during).containsSame(nested),
            () -> assertThat(TestRunRecorder.active()).isEqualTo(before));
    }

    @Test
    @DisplayName("zamknięcie przebiegu, który nie jest już bieżący, nie rusza bieżącego")
    void activate_whenClosedOutOfOrder_leavesLaterRunActive() {

        // given
        Optional<TestRunRecorder> before = TestRunRecorder.active();
        TestRunRecorder first = new TestRunRecorder(clock);
        TestRunRecorder second = new TestRunRecorder(clock);
        TestRunRecorder.Activation firstActivation = first.activate();
        TestRunRecorder.Activation secondActivation = second.activate();

        // when
        firstActivation.close();
        Optional<TestRunRecorder> afterFirstClosed = TestRunRecorder.active();
        // sprzątanie od końca: aktywny przebieg jest wspólny dla całej JVM
        secondActivation.close();
        firstActivation.close();

        // then
        assertAll(
            () -> assertThat(afterFirstClosed).containsSame(second),
            () -> assertThat(TestRunRecorder.active()).isEqualTo(before));
    }

    @Test
    @DisplayName("testy kończone z wielu wątków naraz liczą się wszystkie")
    void testFinished_whenManyThreads_countsEveryTest() {

        // given
        recorder.classStarted(ORDERS);
        int tests = 1_000;

        // when
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            IntStream.range(0, tests).forEach(index -> executor.submit(() -> runTest("t" + index)));
        }

        // then
        TestRunSnapshot snapshot = snapshot();
        assertAll(
            () -> assertThat(snapshot.tests()).isEqualTo(tests),
            () -> assertThat(snapshot.testTimings()).hasSize(tests));
    }

    private void runTest(String uniqueId) {

        recorder.testStarted(uniqueId);
        recorder.testFinished(uniqueId, TestResult.passed(ORDERS, uniqueId));
    }

    private TestRunSnapshot snapshot() {

        return recorder.snapshot(MemorySnapshotFixtures.calm());
    }
}
