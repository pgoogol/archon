package com.pgoogol.music.api;

import java.util.List;

/**
 * Raport z importu własnych playlist — kontrakt {@code POST /music/api/v1/ingest/my-playlists}
 * (tryb C). Playlista, która padła, nie przerywa przebiegu: wraca w {@code failed}
 * z powodem, a resztę widać w {@code imported}.
 */
public record IngestMyPlaylistsResponse(List<IngestPlaylistResponse> imported,
                                        List<FailedPlaylistResponse> failed) {

    public record FailedPlaylistResponse(String spotifyPlaylistId, String name,
                                         String errorCode, String reason) {

    }
}
