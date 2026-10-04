package com.pgoogol.testdiagnostics.core;

import java.util.List;
import java.util.Objects;

/**
 * Powód, dla którego wystartowało nowe środowisko: dane, nie gotowy tekst. Zdanie
 * w wybranym języku składa raport.
 */
public sealed interface EnvironmentCause {

    static EnvironmentCause first() {

        return new First();
    }

    /** Pierwsze środowisko w przebiegu. */
    record First() implements EnvironmentCause {
    }

    /**
     * Ta sama konfiguracja co środowisko o numerze {@code environment}, ale tamtego nie
     * było już w pamięci podręcznej.
     *
     * @param environment numer wcześniejszego środowiska z tą samą konfiguracją
     * @param closedBy    klasa testów, po której {@code @DirtiesContext} zamknął tamto
     *                    środowisko; pusta, gdy wypadło przez limit pamięci podręcznej
     */
    record Reloaded(int environment, String closedBy) implements EnvironmentCause {

        public Reloaded {

            Objects.requireNonNull(closedBy, "klasa zamykająca jest wymagana, pusta, gdy nieznana");
        }

        public boolean closedByDirtiesContext() {

            return !closedBy.isEmpty();
        }
    }

    /**
     * Konfiguracja inna niż w najbliższym wcześniejszym środowisku.
     *
     * @param environment numer najbliższego środowiska
     * @param differences czym się różni, w kolejności atrybutów konfiguracji
     */
    record Differs(int environment, List<AttributeDifference> differences) implements EnvironmentCause {

        public Differs {

            differences = List.copyOf(differences);
        }
    }

    /** Konfiguracji nie dało się opisać; {@code reason} to opis błędu. */
    record Unknown(String reason) implements EnvironmentCause {

        public Unknown {

            Objects.requireNonNull(reason, "opis błędu jest wymagany");
        }
    }
}
