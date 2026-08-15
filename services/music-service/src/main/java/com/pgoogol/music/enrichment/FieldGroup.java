package com.pgoogol.music.enrichment;

/**
 * Grupy pól wzbogacania: METADATA — fakty ze Spotify, AUDIO — fakty
 * z AcousticBrainz/Deezer, AI — estymacje LLM.
 */
public enum FieldGroup {

    METADATA,
    AUDIO,
    AI
}
