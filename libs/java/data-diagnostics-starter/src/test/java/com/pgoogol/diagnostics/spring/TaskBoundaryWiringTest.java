package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Granica zadań w prawdziwym kontekście: harmonogram i wykonawca {@code @Async} Boota. */
class TaskBoundaryWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataDiagnosticsAutoConfiguration.class,
            TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
        .withPropertyValues("diagnostics.mode=dev", "diagnostics.output.jsonl.enabled=false")
        .withUserConfiguration(Jobs.class);

    @Test
    @DisplayName("metoda @Scheduled uruchomiona przez harmonogram Boota dostaje jednostkę scheduled z nazwą metody")
    void scheduledMethod_whenRunByScheduler_reportsScheduledUnit() {

        // when & then
        runner.run(context -> {

            Reports reports = context.getBean(Reports.class);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(reports.described())
                .contains("scheduled:Worker.tick:1"));
        });
    }

    @Test
    @DisplayName("metoda @Async wywołana w otwartej jednostce liczy swoje zapytanie w tej jednostce")
    void asyncMethod_whenCalledInsideUnit_recordsIntoCallerUnit() {

        // when & then
        runner.run(context -> {

            DiagnosticsEngine engine = context.getBean(DiagnosticsEngine.class);
            Worker worker = context.getBean(Worker.class);
            try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

                worker.inBackground().join();
            }
            Reports reports = context.getBean(Reports.class);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(reports.described())
                .contains("http:GET /orders:1"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableAsync
    static class Jobs {

        @Bean
        Reports reports() {

            return new Reports();
        }

        @Bean
        Worker worker(DiagnosticsEngine engine) {

            return new Worker(engine);
        }
    }

    /** Zadania „aplikacji”; każde wykonuje jedno zapytanie. */
    static class Worker {

        private final DiagnosticsEngine engine;

        Worker(DiagnosticsEngine engine) {

            this.engine = engine;
        }

        @Scheduled(fixedDelay = 50)
        public void tick() {

            engine.record(DataAccessEventFixtures.read("select 1"));
        }

        @Async
        public CompletableFuture<Void> inBackground() {

            engine.record(DataAccessEventFixtures.read("select 2"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Reporter zapamiętujący jednostki jako {@code typ:nazwa:liczba operacji}. */
    static class Reports implements FindingReporter {

        private final List<UnitOfWorkSummary> units = new CopyOnWriteArrayList<>();

        @Override
        public void report(UnitOfWork unit, List<Finding> findings) {

            units.add(unit.summary());
        }

        List<String> described() {

            return units.stream()
                .map(unit -> unit.type().name() + ":" + unit.name() + ":" + unit.operationCount())
                .toList();
        }
    }
}
