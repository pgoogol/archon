package com.pgoogol.music.ingestion;

/**
 * Poprawny wiersz CSV po walidacji; {@code album} jest opcjonalny.
 */
public record ParsedTrack(String spotifyId, String title, String artist, String album) {

}
