package com.pgoogol.music.api;

import java.math.BigDecimal;

/**
 * Szacunek zlecenia wzbogacania (M5.1) — kontrakt
 * {@code POST /music/api/v1/enrich/estimate}. Nic nie uruchamia.
 *
 * @param aiTracks      utwory, za które realnie zapłacimy (tylko grupa AI)
 * @param estimatedCost {@code null} = brak stawek w konfiguracji, nie zero
 * @param withinLimit   {@code false} oznacza, że {@code POST /music/api/v1/enrich}
 *                      odrzuci to zlecenie
 */
public record EnrichmentEstimateResponse(
    long trackCount,
    long aiTracks,
    BigDecimal estimatedCost,
    int limit,
    boolean withinLimit) {

}
