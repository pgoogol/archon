package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.activityFindings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PersistenceAnalyzersTest {

    private final FlushAnalyzer flush =
        new FlushAnalyzer(SeverityScale.of(10), SeverityScale.of(50), SeverityScale.of(1_000));

    @Test
    @DisplayName("spokojna sesja nie daje żadnego wniosku o flushach")
    void flush_whenFewFastSmallFlushes_reportsNothing() {

        // given
        List<PersistenceSessionEvent> events = List.of(session().flushes(3, 2).longestFlushMillis(49).largestFlush(999).build());

        // when
        List<Finding> findings = activityFindings(flush, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("flushe pełne i automatyczne z wielu sesji sumują się do progu liczby flushy")
    void flush_whenFlushesAcrossSessionsReachThreshold_reportsFlushCount() {

        // given
        List<PersistenceSessionEvent> events = List.of(session().flushes(4, 1).build(), session().flushes(2, 3).build());

        // when
        List<Finding> findings = activityFindings(flush, events);

        // then
        assertAll(
            () -> assertThat(findings).extracting(Finding::code).containsExactly("FLUSH_COUNT"),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(10),
            () -> assertThat(findings.getFirst().severity()).isEqualTo(Severity.WARN));
    }

    @Test
    @DisplayName("wolny i duży flush dają osobne wnioski z najgorszym pomiarem")
    void flush_whenSlowAndLarge_reportsBothFindings() {

        // given
        List<PersistenceSessionEvent> events = List.of(
            session().flushes(1, 0).longestFlushMillis(80).largestFlush(200).build(),
            session().flushes(1, 0).longestFlushMillis(300).largestFlush(6_000).build());

        // when
        List<Finding> findings = activityFindings(flush, events);

        // then
        assertAll(
            () -> assertThat(findings).extracting(Finding::code).containsExactly("SLOW_FLUSH", "LARGE_FLUSH"),
            () -> assertThat(findings).extracting(Finding::measured).containsExactly(300L, 6_000L),
            () -> assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL, Severity.CRITICAL));
    }

    @Test
    @DisplayName("czas sprawdzania zmian sumuje się z sesji i od progu daje wniosek z podpowiedzią")
    void dirtyChecking_whenTotalTimeReachesThreshold_reportsWarn() {

        // given
        DirtyCheckingAnalyzer analyzer = new DirtyCheckingAnalyzer(SeverityScale.of(50));
        List<PersistenceSessionEvent> below = List.of(session().dirtyCheckMillis(49).build());
        List<PersistenceSessionEvent> reached = List.of(session().dirtyCheckMillis(30).build(), session().dirtyCheckMillis(20).build());

        // when
        List<Finding> none = activityFindings(analyzer, below);
        List<Finding> findings = activityFindings(analyzer, reached);

        // then
        assertAll(
            () -> assertThat(none).isEmpty(),
            () -> assertThat(findings).extracting(Finding::code).containsExactly("DIRTY_CHECKING"),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(50),
            () -> assertThat(findings.getFirst().title()).contains("readOnly"));
    }

    @Test
    @DisplayName("encje załadowane we wszystkich sesjach jednostki liczą się razem")
    void entityLoad_whenLoadsAcrossSessionsReachThreshold_reportsWarn() {

        // given
        EntityLoadAnalyzer analyzer = new EntityLoadAnalyzer(SeverityScale.of(1_000));
        List<PersistenceSessionEvent> below = List.of(session().entitiesLoaded(999).build());
        List<PersistenceSessionEvent> reached = List.of(session().entitiesLoaded(600).build(), session().entitiesLoaded(400).build());

        // when
        List<Finding> none = activityFindings(analyzer, below);
        List<Finding> findings = activityFindings(analyzer, reached);

        // then
        assertAll(
            () -> assertThat(none).isEmpty(),
            () -> assertThat(findings).extracting(Finding::code).containsExactly("ENTITY_LOAD"),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(1_000));
    }

    @Test
    @DisplayName("cache trafiający rzadziej niż w połowie odczytów daje wskazówkę INFO z procentem trafień")
    void secondLevelCache_whenHitRatioLow_reportsInfo() {

        // given
        SecondLevelCacheAnalyzer analyzer = new SecondLevelCacheAnalyzer(20);
        List<PersistenceSessionEvent> events = List.of(session().cache(4, 16).build());

        // when
        List<Finding> findings = activityFindings(analyzer, events);

        // then
        assertAll(
            () -> assertThat(findings).extracting(Finding::code).containsExactly("SECOND_LEVEL_CACHE"),
            () -> assertThat(findings.getFirst().severity()).isEqualTo(Severity.INFO),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(20));
    }

    @Test
    @DisplayName("za mało odczytów albo dobry współczynnik trafień nie dają wniosku o cache")
    void secondLevelCache_whenFewLookupsOrGoodRatio_reportsNothing() {

        // given
        SecondLevelCacheAnalyzer analyzer = new SecondLevelCacheAnalyzer(20);
        List<PersistenceSessionEvent> few = List.of(session().cache(0, 19).build());
        List<PersistenceSessionEvent> good = List.of(session().cache(10, 10).build());

        // when
        List<Finding> fewFindings = activityFindings(analyzer, few);
        List<Finding> goodFindings = activityFindings(analyzer, good);

        // then
        assertThat(fewFindings).isEmpty();
        assertThat(goodFindings).isEmpty();
    }

    private static SessionBuilder session() {

        return new SessionBuilder();
    }

    /** Builder zdarzenia sesji: test ustawia tylko wielkości, które sprawdza. */
    private static final class SessionBuilder {

        private long flushes;

        private long autoFlushes;

        private long longestFlushMillis;

        private long largestFlush;

        private long dirtyCheckMillis;

        private long entitiesLoaded;

        private long cacheHits;

        private long cacheMisses;

        SessionBuilder flushes(long full, long auto) {

            this.flushes = full;
            this.autoFlushes = auto;
            return this;
        }

        SessionBuilder longestFlushMillis(long value) {

            this.longestFlushMillis = value;
            return this;
        }

        SessionBuilder largestFlush(long value) {

            this.largestFlush = value;
            return this;
        }

        SessionBuilder dirtyCheckMillis(long value) {

            this.dirtyCheckMillis = value;
            return this;
        }

        SessionBuilder entitiesLoaded(long value) {

            this.entitiesLoaded = value;
            return this;
        }

        SessionBuilder cache(long hits, long misses) {

            this.cacheHits = hits;
            this.cacheMisses = misses;
            return this;
        }

        PersistenceSessionEvent build() {

            Duration longest = Duration.ofMillis(longestFlushMillis);
            Duration dirty = Duration.ofMillis(dirtyCheckMillis);
            return new PersistenceSessionEvent(flushes, autoFlushes, longest, longest, largestFlush, 0, dirty,
                entitiesLoaded, cacheHits, cacheMisses, 0);
        }
    }
}
