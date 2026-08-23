package com.pgoogol.music.playlist;

/**
 * Snapshot playlisty z ostatniego udanego importu — tyle, ile trzeba, żeby
 * rozstrzygnąć, czy playlistę w ogóle pobierać ze Spotify. Projekcja zamiast
 * encji: import hurtem pyta o kilkadziesiąt playlist naraz i nie potrzebuje
 * z nich niczego więcej.
 */
public record PlaylistSnapshot(String spotifyPlaylistId, String snapshotId) {

}
