package com.pgoogol.music.library;

/**
 * Skąd wpis trafił do biblioteki (tryby ingestion): plik CSV, własna playlista,
 * cudza playlista.
 */
public enum LibrarySource {

    FILE,
    PLAYLIST,
    FOREIGN_PLAYLIST
}
