package com.pgoogol.music.enrichment.bpm;

import com.pgoogol.music.catalog.BpmSource;

/**
 * Wynik kaskady BPM — wartość po ewentualnej korekcie half-time
 * + źródło do audytu w {@code track_catalog.bpm_source}.
 */
public record BpmResolution(int bpm, BpmSource source) {

}
