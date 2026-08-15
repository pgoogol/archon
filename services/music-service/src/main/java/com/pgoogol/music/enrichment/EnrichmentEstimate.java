package com.pgoogol.music.enrichment;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Ile utworów obejmie zlecenie i ile to będzie kosztowało (M5.1) — koszt
 * pokazujemy <b>przed</b> startem joba, nie po.
 *
 * @param aiTracks       utwory, za które realnie zapłacimy; grupy METADATA i AUDIO
 *                       jadą z darmowych źródeł, więc nie wchodzą do kosztu
 * @param estimatedCost  {@code null}, gdy stawki providera nie są skonfigurowane —
 *                       lepiej powiedzieć „nie wiem" niż zgadywać cennik
 * @param limit          twardy sufit {@code llm.max-tracks-per-job}, liczony
 *                       wyłącznie po {@link #aiTracks}
 * @param withinLimit    czy zlecenie w ogóle wystartuje; zlecenie bez grupy AI
 *                       nie ma sufitu, bo nie ma czego chronić — sufit
 *                       pilnuje rachunku za LLM, a nie wielkości przebiegu
 */
public record EnrichmentEstimate(
    long trackCount,
    long aiTracks,
    @Nullable BigDecimal estimatedCost,
    int limit,
    boolean withinLimit) {

}
