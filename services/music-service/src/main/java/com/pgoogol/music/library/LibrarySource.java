package com.pgoogol.music.library;

/**
 * Skąd wpis trafił do biblioteki (tryby ingestion; D17): plik CSV, własna playlista,
 * cudza playlista.
 */
public enum LibrarySource {

    FILE,
    PLAYLIST,
    FOREIGN_PLAYLIST
}
