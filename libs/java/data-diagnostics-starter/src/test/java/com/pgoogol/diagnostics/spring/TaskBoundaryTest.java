package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class TaskBoundaryTest {

    private final BoundaryTestSupport support = new BoundaryTestSupport();

    private final DiagnosticsEngine engine = support.engine();

    private final TaskBoundary boundary = new TaskBoundary(() -> engine);

    @Test
    @DisplayName("zadanie zlecone z otwartej jednostki liczy swoje zapytania w jednostce rodzica")
    void decorate_whenParentUnitOpen_recordsIntoParent() throws Exception {

        // given
        Runnable task = () -> engine.record(DataAccessEventFixtures.read("select 2"));

        // when
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            engine.record(DataAccessEventFixtures.read("select 1"));
            Future<?> done = executor.submit(boundary.decorate(task));
            done.get();
        }

        // then
        assertThat(support.described()).containsExactly("http:GET /orders:2");
    }

    @Test
    @DisplayName("jednostka rodzica czeka z raportem na zadanie, które do niej dołączyło")
    void decorate_whenParentClosesFirst_unitReportsAfterTask() throws Exception {

        // given
        Runnable task = () -> engine.record(DataAccessEventFixtures.read("select 2"));
        Runnable decorated;
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            decorated = joinNow(task);
        }

        // when: rodzic już wyszedł, ale zadanie zdążyło dołączyć, zanim to zrobił
        decorated.run();

        // then
        assertThat(support.described()).containsExactly("http:GET /orders:1");
    }

    @Test
    @DisplayName("zadanie, którego rodzic zdążył się zamknąć, dostaje własną jednostkę async")
    void decorate_whenParentAlreadyClosed_opensAsyncUnit() {

        // given
        Runnable task = () -> engine.record(DataAccessEventFixtures.read("select 2"));
        Runnable decorated;
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            decorated = boundary.decorate(task);
        }

        // when
        decorated.run();

        // then
        assertThat(support.described()).containsExactly("http:GET /orders:0", "async:async:1");
    }

    @Test
    @DisplayName("metoda @Scheduled dostaje jednostkę scheduled nazwaną klasą i metodą")
    void decorate_whenScheduledMethod_opensScheduledUnitNamedAfterMethod() throws NoSuchMethodException {

        // given
        ScheduledMethodRunnable scheduled = new ScheduledMethodRunnable(new NightlyJob(engine), "run");

        // when
        boundary.decorate(scheduled).run();

        // then
        assertThat(support.described()).containsExactly("scheduled:NightlyJob.run:1");
    }

    /** Zadanie, które dołącza do jednostki od razu, w wątku rodzica, a pracę wykonuje później. */
    private Runnable joinNow(Runnable task) {

        UnitOfWorkScope joined = engine.handoff().flatMap(engine::join).orElseThrow();
        return () -> {

            try (joined) {

                task.run();
            }
        };
    }

    /** Zadanie harmonogramu „aplikacji”. */
    public static final class NightlyJob {

        private final DiagnosticsEngine engine;

        NightlyJob(DiagnosticsEngine engine) {

            this.engine = engine;
        }

        public void run() {

            engine.record(DataAccessEventFixtures.read("select 1"));
        }
    }
}
