package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Ustala miejsce w kodzie aplikacji, z którego wyszła operacja. Strategia przechwytywania
 * woła go w wątku, który wykonuje operację, zanim zdarzenie trafi do silnika.
 *
 * <p>Implementacja sama decyduje, czy w ogóle chodzić po stosie: to kosztuje, więc w prod
 * robi to tylko dla operacji wolnych i powtarzanych. Stąd kształt i czas w argumentach.</p>
 */
@FunctionalInterface
public interface CallSiteResolver {

    /** Nie ustala niczego; dla aplikacji bez znanych pakietów i dla testów. */
    CallSiteResolver NONE = (store, shape, duration) -> Optional.empty();

    Optional<CallSite> resolve(DataStore store, String shape, Duration duration);
}
