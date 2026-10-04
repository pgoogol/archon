package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.report.ShapeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

class ShapeStatsTest {

    private static final String BY_ID = "select * from orders where id = ?";

    @Test
    @DisplayName("powtórzony kształt sumuje liczbę wykonań, łączny i najdłuższy czas")
    void add_whenSameShapeRepeated_aggregatesCountAndTimes() {

        // given
        ShapeStats stats = new ShapeStats(10);

        // when
        Stream.of(2L, 5L, 3L)
            .map(millis -> read(BY_ID).millis(millis).build())
            .forEach(stats::add);

        // then
        ShapeSummary summary = single(stats);
        assertAll(
            () -> assertThat(summary.count()).isEqualTo(3),
            () -> assertThat(summary.totalTime()).isEqualTo(Duration.ofMillis(10)),
            () -> assertThat(summary.maxTime()).isEqualTo(Duration.ofMillis(5)),
            () -> assertThat(summary.kind()).isEqualTo(OperationKind.READ),
            () -> assertThat(summary.overflow()).isFalse());
    }

    @Test
    @DisplayName("nieudane wykonania liczą się do kształtu i osobno jako błędy")
    void add_whenSomeExecutionsFail_countsFailuresSeparately() {

        // given
        ShapeStats stats = new ShapeStats(10);

        // when
        stats.add(read(BY_ID).build());
        stats.add(read(BY_ID).failed().build());

        // then
        ShapeSummary summary = single(stats);
        assertAll(
            () -> assertThat(summary.count()).isEqualTo(2),
            () -> assertThat(summary.failures()).isEqualTo(1));
    }

    @Test
    @DisplayName("przykładem kształtu zostaje pierwszy pełny tekst")
    void add_whenTextsDiffer_keepsFirstSample() {

        // given
        ShapeStats stats = new ShapeStats(10);

        // when
        stats.add(read(BY_ID).text("select * from orders where id = 1").build());
        stats.add(read(BY_ID).text("select * from orders where id = 2").build());

        // then
        assertThat(single(stats).sample()).isEqualTo("select * from orders where id = 1");
    }

    @Test
    @DisplayName("kształt pamięta do trzech różnych miejsc wywołania w kolejności pojawienia się")
    void add_whenManyCallers_keepsThreeDistinct() {

        // given
        ShapeStats stats = new ShapeStats(10);

        // when
        Stream.of("a", "a", "b", "c", "d")
            .map(method -> read(BY_ID).calledFrom("com.example.OrderService", method, 10).build())
            .forEach(stats::add);

        // then
        List<String> methods = single(stats).callers().stream()
            .map(CallSite::method)
            .toList();
        assertThat(methods).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("ponad limit nowe kształty trafiają do kubełka zbiorczego, a znane liczą się dalej")
    void add_whenOverShapeLimit_sendsNewShapesToOverflowBucket() {

        // given
        ShapeStats stats = new ShapeStats(2);

        // when
        Stream.of("select 1", "select 2", "select 3", "select 4", "select 1")
            .map(shape -> read(shape).build())
            .forEach(stats::add);

        // then
        List<ShapeSummary> summaries = stats.summaries();
        ShapeSummary bucket = summaries.getLast();
        assertAll(
            () -> assertThat(summaries).extracting(ShapeSummary::shape)
                .containsExactly("select 1", "select 2", ShapeStats.OVERFLOW_SHAPE),
            () -> assertThat(summaries.getFirst().count()).isEqualTo(2),
            () -> assertThat(bucket.overflow()).isTrue(),
            () -> assertThat(bucket.count()).isEqualTo(2),
            () -> assertThat(bucket.kind()).isEqualTo(OperationKind.OTHER),
            () -> assertThat(bucket.sample()).isNull());
    }

    @Test
    @DisplayName("ten sam kształt w dwóch magazynach to dwa kształty, a kubełek zbiorczy jest osobny na magazyn")
    void add_whenShapesComeFromTwoStores_keepsThemApart() {

        // given
        ShapeStats stats = new ShapeStats(2);

        // when
        stats.add(read("get ?").build());
        stats.add(read("get ?").store("redis").build());
        stats.add(read("other ?").store("redis").build());

        // then
        assertThat(stats.summaries())
            .extracting(summary -> summary.store().name() + ":" + summary.shape())
            .containsExactly("postgresql:get ?", "redis:get ?", "redis:" + ShapeStats.OVERFLOW_SHAPE);
    }

    @Test
    @DisplayName("limit kształtów musi zostawić miejsce choć na jeden kształt")
    void constructor_whenLimitZero_fails() {

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ShapeStats(0))
            .withMessageContaining("limit kształtów");
    }

    private static ShapeSummary single(ShapeStats stats) {

        List<ShapeSummary> summaries = stats.summaries();
        assertThat(summaries).hasSize(1);
        return summaries.getFirst();
    }
}
