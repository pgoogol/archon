package com.pgoogol.music.ingestion;

/**
 * Playlista, której przebieg nie pobierał: albo nie zmieniła się od ostatniego
 * importu, albo przebieg skończył się wcześniej (wyczerpana kwota Spotify)
 * i nie doszła kolej. W obu wypadkach nic nie jest zepsute — to informacja,
 * czego szukać po powtórzeniu importu.
 */
public record SkippedPlaylist(String spotifyPlaylistId, String name) {

}
