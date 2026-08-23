package com.pgoogol.music.api;

/**
 * Wynik eksportu setu — kontrakt {@code POST /music/api/v1/playlists/{id}/export-to-spotify}.
 */
public record PlaylistExportResponse(
    Long playlistId,
    String spotifyPlaylistId,
    String name,
    int exportedTracks,
    boolean created,
    String spotifyUrl) {

}
