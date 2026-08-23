package com.pgoogol.music.enrichment.spotify;

/**
 * Nagłówek playlisty Spotify (M2.1). {@code ownerId} rozstrzyga, czy playlista
 * należy do właściciela biblioteki (source=PLAYLIST) czy jest cudza
 * (source=FOREIGN_PLAYLIST).
 *
 * <p>{@code snapshotId} to wersja zawartości nadana przez Spotify: ta sama
 * wartość co przy poprzednim imporcie oznacza, że playlista nie zmieniła się
 * i nie ma po co pobierać jej utworów.</p>
 */
public record SpotifyPlaylist(
    String spotifyPlaylistId,
    String name,
    String ownerId,
    String ownerDisplayName,
    int trackCount,
    String snapshotId) {

}
