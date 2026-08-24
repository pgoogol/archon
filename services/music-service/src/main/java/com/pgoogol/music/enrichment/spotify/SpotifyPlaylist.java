package com.pgoogol.music.enrichment.spotify;

import org.springframework.lang.Nullable;

/**
 * Nagłówek playlisty Spotify (M2.1). {@code ownerId} rozstrzyga, czy playlista
 * należy do właściciela biblioteki (source=PLAYLIST) czy jest cudza
 * (source=FOREIGN_PLAYLIST).
 *
 * <p>{@code snapshotId} to wersja zawartości nadana przez Spotify: ta sama
 * wartość co przy poprzednim imporcie oznacza, że playlista nie zmieniła się
 * i nie ma po co pobierać jej utworów.</p>
 *
 * <p>{@code trackCount} puste oznacza, że Spotify nie podał liczby utworów —
 * to nie to samo co playlista pusta.</p>
 */
public record SpotifyPlaylist(
    String spotifyPlaylistId,
    String name,
    String ownerId,
    String ownerDisplayName,
    @Nullable Integer trackCount,
    String snapshotId) {

}
