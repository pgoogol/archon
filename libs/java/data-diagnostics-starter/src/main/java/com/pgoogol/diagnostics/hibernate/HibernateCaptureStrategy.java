package com.pgoogol.diagnostics.hibernate;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Strategia przechwytywania wnętrza Hibernate. Robi trzy rzeczy: dopisuje do ustawień
 * Hibernate słuchacza sesji ({@link #SESSION_LISTENER_SETTING}), po starcie rejestruje
 * w każdej fabryce sesji licznik załadowanych encji i przekazuje silnikowi zdarzenie
 * każdej zakończonej sesji.
 *
 * <p>Ustawienia słuchacza sesji nie nadpisuje, gdy aplikacja ma własnego: wtedy zdarzeń
 * sesji nie będzie, a w logu zostaje ostrzeżenie.</p>
 */
public class HibernateCaptureStrategy implements SmartInitializingSingleton, DisposableBean {

    public static final String SESSION_LISTENER_SETTING = "hibernate.session.events.auto";

    private static final Logger log = LoggerFactory.getLogger(HibernateCaptureStrategy.class);

    private final Supplier<DiagnosticsEngine> engine;

    private final Supplier<List<EntityManagerFactory>> factories;

    public HibernateCaptureStrategy(Supplier<DiagnosticsEngine> engine, Supplier<List<EntityManagerFactory>> factories) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
        this.factories = Objects.requireNonNull(factories, "dostęp do fabryk sesji jest wymagany");
    }

    /** Dopisuje słuchacza sesji do ustawień Hibernate, o ile aplikacja nie ustawiła własnego. */
    public void customize(Map<String, Object> hibernateProperties) {

        Object existing = hibernateProperties.putIfAbsent(SESSION_LISTENER_SETTING,
            DiagnosticsSessionEventListener.class.getName());
        if (Objects.nonNull(existing)) {

            log.warn("Aplikacja ma własne {}={}; diagnostyka nie zobaczy flushy ani cache tej fabryki sesji",
                SESSION_LISTENER_SETTING, existing);
        }
    }

    @Override
    public void afterSingletonsInstantiated() {

        HibernateSessionBridge.register(this);
        PostLoadEventListener loadCounter = event -> HibernateSessionBridge.entityLoaded();
        factories.get().forEach(factory -> countLoads(factory, loadCounter));
    }

    @Override
    public void destroy() {

        HibernateSessionBridge.unregister(this);
    }

    void sessionEnded(PersistenceSessionEvent event) {

        try {

            engine.get().recordActivity(event);
        } catch (RuntimeException | LinkageError failure) {

            log.debug("Nie udało się przekazać zdarzenia sesji Hibernate", failure);
        }
    }

    private void countLoads(EntityManagerFactory factory, PostLoadEventListener loadCounter) {

        SessionFactoryImplementor sessionFactory = factory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry = sessionFactory.getEventEngine().getListenerRegistry();
        registry.appendListeners(EventType.POST_LOAD, loadCounter);
    }
}
