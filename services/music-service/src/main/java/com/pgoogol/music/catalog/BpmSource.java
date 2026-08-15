package com.pgoogol.music.catalog;

/**
 * Źródło wartości BPM — audyt kaskady BPM:
 * metryki ręczne → AcousticBrainz → Deezer → LLM.
 */
public enum BpmSource {

    MANUAL,
    ACOUSTICBRAINZ,
    DEEZER,
    LLM
}
