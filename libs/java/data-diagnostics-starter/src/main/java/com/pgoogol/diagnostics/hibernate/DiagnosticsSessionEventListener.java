package com.pgoogol.diagnostics.hibernate;

import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
import org.hibernate.BaseSessionEventListener;

import java.time.Duration;

/**
 * Słuchacz zdarzeń jednej sesji Hibernate. Hibernate tworzy nową instancję dla każdej
 * sesji (ustawienie {@code hibernate.session.events.auto}), więc pola są licznikami tej
 * jednej sesji. Na końcu sesji składa je w jedno zdarzenie; sesja, która nic nie robiła,
 * zdarzenia nie daje.
 *
 * <p>Encje załadowane liczy osobny słuchacz {@code POST_LOAD}, bo sesja nie ma takiego
 * zdarzenia; licznik jest na wątek, a sesja jest związana z wątkiem.</p>
 */
public class DiagnosticsSessionEventListener extends BaseSessionEventListener {

    private long startedAt;

    private long flushes;

    private long autoFlushes;

    private long flushNanos;

    private long longestFlushNanos;

    private long largestFlush;

    private long dirtyChecks;

    private long dirtyCheckNanos;

    private long cacheHits;

    private long cacheMisses;

    private long cachePuts;

    @Override
    public void flushStart() {

        startedAt = System.nanoTime();
    }

    @Override
    public void flushEnd(int numberOfEntities, int numberOfCollections) {

        flushes++;
        flushed(numberOfEntities);
    }

    @Override
    public void partialFlushStart() {

        startedAt = System.nanoTime();
    }

    @Override
    public void partialFlushEnd(int numberOfEntities, int numberOfCollections) {

        autoFlushes++;
        flushed(numberOfEntities);
    }

    @Override
    public void dirtyCalculationStart() {

        startedAt = System.nanoTime();
    }

    @Override
    public void dirtyCalculationEnd(boolean dirty) {

        dirtyChecks++;
        dirtyCheckNanos += System.nanoTime() - startedAt;
    }

    @Override
    public void cacheGetEnd(boolean hit) {

        if (hit) {

            cacheHits++;
            return;
        }
        cacheMisses++;
    }

    @Override
    public void cachePutEnd() {

        cachePuts++;
    }

    @Override
    public void end() {

        PersistenceSessionEvent event = new PersistenceSessionEvent(flushes, autoFlushes,
            Duration.ofNanos(flushNanos), Duration.ofNanos(longestFlushNanos), largestFlush, dirtyChecks,
            Duration.ofNanos(dirtyCheckNanos), HibernateSessionBridge.takeLoaded(), cacheHits, cacheMisses, cachePuts);
        if (event.hasActivity()) {

            HibernateSessionBridge.sessionEnded(event);
        }
    }

    private void flushed(int numberOfEntities) {

        long nanos = System.nanoTime() - startedAt;
        flushNanos += nanos;
        longestFlushNanos = Math.max(longestFlushNanos, nanos);
        largestFlush = Math.max(largestFlush, numberOfEntities);
    }
}
