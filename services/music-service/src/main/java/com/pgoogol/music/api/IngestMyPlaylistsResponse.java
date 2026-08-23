package com.pgoogol.music.api;

import java.util.List;

/**
 * Raport z importu własnych playlist — kontrakt {@code POST /music/api/v1/ingest/my-playlists}
 * (tryb C). Playlista, która padła, nie przerywa przebiegu: wraca w {@code failed}
 * z powodem, a resztę widać w {@code imported}.
 *
 * <p>{@code unchanged} to playlisty pominięte świadomie, bo nie zmieniły się od
 * ostatniego importu; {@code notAttempted} — te, do których przebieg nie doszedł,
 * bo Spotify wstrzymał ruch. Obie listy mówią DJ-owi, że nic nie zginęło.</p>
 */
public record IngestMyPlaylistsResponse(List<IngestPlaylistResponse> imported,
                                        List<FailedPlaylistResponse> failed,
                                        List<SkippedPlaylistResponse> unchanged,
                                        List<SkippedPlaylistResponse> notAttempted) {

    public record FailedPlaylistResponse(String spotifyPlaylistId, String name,
                                         String errorCode, String reason) {

    }

    public record SkippedPlaylistResponse(String spotifyPlaylistId, String name) {

    }
}
