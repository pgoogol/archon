package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Podsumowanie jednej zakończonej sesji warstwy utrwalania (sesji Hibernate): co robiła
 * poza samymi zapytaniami.
 *
 * @param flushes         liczba pełnych flushy
 * @param autoFlushes     liczba automatycznych flushy przed zapytaniem
 * @param flushTime       łączny czas wszystkich flushy
 * @param longestFlush    najdłuższy flush
 * @param largestFlush    największa liczba encji w jednym flushu
 * @param dirtyChecks     liczba encji sprawdzonych pod kątem zmian
 * @param dirtyCheckTime  łączny czas sprawdzania zmian
 * @param entitiesLoaded  liczba załadowanych encji
 * @param cacheHits       trafienia w cache drugiego poziomu
 * @param cacheMisses     chybienia cache drugiego poziomu
 * @param cachePuts       zapisy do cache drugiego poziomu
 */
public record PersistenceSessionEvent(
    long flushes,
    long autoFlushes,
    Duration flushTime,
    Duration longestFlush,
    long largestFlush,
    long dirtyChecks,
    Duration dirtyCheckTime,
    long entitiesLoaded,
    long cacheHits,
    long cacheMisses,
    long cachePuts) implements ActivityEvent {

    public PersistenceSessionEvent {

        Objects.requireNonNull(flushTime, "czas flushy jest wymagany");
        Objects.requireNonNull(longestFlush, "najdłuższy flush jest wymagany");
        Objects.requireNonNull(dirtyCheckTime, "czas sprawdzania zmian jest wymagany");
    }

    /** Czy sesja zrobiła cokolwiek, co obchodzi analizy; pusta sesja nie jest warta zdarzenia. */
    public boolean hasActivity() {

        return flushes + autoFlushes + dirtyChecks + entitiesLoaded + cacheHits + cacheMisses + cachePuts > 0;
    }
}
