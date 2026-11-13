package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;

import java.util.List;

/**
 * Niski współczynnik trafień cache drugiego poziomu w jednostce pracy: encje oznaczone
 * jako cache'owane są odczytywane, ale większość odczytów i tak idzie do bazy. Cache,
 * który nie trafia, kosztuje pamięć i zapisy bez zysku. Wniosek ma wagę {@code INFO}
 * i powstaje dopiero od {@code minimumLookups} odczytów, żeby rozgrzewanie cache nie
 * dawało szumu; pomiarem jest procent trafień.
 */
public class SecondLevelCacheAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "second-level-cache";

    public static final String CODE = "SECOND_LEVEL_CACHE";

    public static final long DEFAULT_MINIMUM_LOOKUPS = 20;

    public static final int MINIMUM_HIT_PERCENT = 50;

    private static final String TITLE = "Cache drugiego poziomu trafił w %d%% z %d odczytów (oczekiwane co najmniej %d%%)";

    private final long minimumLookups;

    /** @param minimumLookups od ilu odczytów cache w jednostce oceniać współczynnik trafień */
    public SecondLevelCacheAnalyzer(long minimumLookups) {

        if (minimumLookups < 1) {

            throw new IllegalArgumentException("minimalna liczba odczytów cache musi być dodatnia: " + minimumLookups);
        }
        this.minimumLookups = minimumLookups;
    }

    public static SecondLevelCacheAnalyzer defaults(DiagnosticsSettings settings) {

        return new SecondLevelCacheAnalyzer(DEFAULT_MINIMUM_LOOKUPS);
    }

    @Override
    public String id() {

        return ID;
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new Session();
    }

    private final class Session implements AnalysisSession {

        private long hits;

        private long misses;

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof PersistenceSessionEvent session) {

                hits += session.cacheHits();
                misses += session.cacheMisses();
            }
        }

        @Override
        public List<Finding> findings() {

            long lookups = hits + misses;
            if (lookups < minimumLookups) {

                return List.of();
            }
            long hitPercent = hits * 100 / lookups;
            if (hitPercent >= MINIMUM_HIT_PERCENT) {

                return List.of();
            }
            String title = TITLE.formatted(hitPercent, lookups, MINIMUM_HIT_PERCENT);
            Finding finding = new Finding(CODE, Severity.INFO, title, hitPercent, MINIMUM_HIT_PERCENT, List.of());
            return List.of(finding);
        }
    }
}
