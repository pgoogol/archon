package com.pgoogol.music.ingestion;

import java.util.List;

/**
 * Raport importu wszystkich własnych playlist (tryb C): {@code imported} to
 * playlisty domknięte, {@code failed} — te, które padły po drodze. Jedna
 * playlista nie może przerwać całego przebiegu, bo przy kilkudziesięciu
 * playlistach powtarzanie wszystkiego od zera kosztuje kwadranse.
 *
 * <p>{@code unchanged} to playlisty o niezmienionym snapshocie — świadomie
 * nietknięte, bo pobranie ich niczego by nie wniosło, a kosztuje kwotę.
 * {@code notAttempted} zbiera te, do których przebieg nie doszedł, bo Spotify
 * wstrzymał ruch; wystarczy powtórzyć import, gdy kwota się odnowi.</p>
 */
public record MyPlaylistsIngestReport(List<PlaylistIngestReport> imported,
                                      List<FailedPlaylist> failed,
                                      List<SkippedPlaylist> unchanged,
                                      List<SkippedPlaylist> notAttempted) {

}
