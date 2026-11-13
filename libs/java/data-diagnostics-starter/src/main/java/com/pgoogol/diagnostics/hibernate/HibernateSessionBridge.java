package com.pgoogol.diagnostics.hibernate;

import com.pgoogol.diagnostics.core.PersistenceSessionEvent;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Most między słuchaczem sesji, którego Hibernate tworzy sam po nazwie klasy, a strategią,
 * która jest beanem Springa. Hibernate nie przyjmuje gotowej instancji słuchacza sesji,
 * więc jedynym wspólnym miejscem jest stan statyczny: lista aktywnych strategii i licznik
 * encji załadowanych w bieżącym wątku.
 *
 * <p>Każda strategia oddaje zdarzenie własnemu silnikowi, a silnik bez otwartej jednostki
 * w tym wątku je pomija, więc kilka kontekstów aplikacji w jednej JVM sobie nie przeszkadza.</p>
 */
final class HibernateSessionBridge {

    private static final CopyOnWriteArrayList<HibernateCaptureStrategy> STRATEGIES = new CopyOnWriteArrayList<>();

    private static final ThreadLocal<long[]> LOADED = ThreadLocal.withInitial(() -> new long[1]);

    private HibernateSessionBridge() {
    }

    static void register(HibernateCaptureStrategy strategy) {

        STRATEGIES.addIfAbsent(strategy);
    }

    static void unregister(HibernateCaptureStrategy strategy) {

        STRATEGIES.remove(strategy);
    }

    static void sessionEnded(PersistenceSessionEvent event) {

        STRATEGIES.forEach(strategy -> strategy.sessionEnded(event));
    }

    static void entityLoaded() {

        LOADED.get()[0]++;
    }

    /** Liczba encji załadowanych w tym wątku od poprzedniego wywołania. */
    static long takeLoaded() {

        long[] counter = LOADED.get();
        long loaded = counter[0];
        LOADED.remove();
        return loaded;
    }
}
