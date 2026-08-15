package com.pgoogol.music.api;

import java.time.Instant;
import java.util.List;

/**
 * Playlista z pełnym składem setu — kontrakt {@code GET /api/playlists/{id}}.
 * {@code djSlot} to slot obowiązujący (override DJ-a albo wyliczenie wg faz wieczoru),
 * {@code djSlotOverride} pokazuje, czy wartość jest ręczna.
 */
public record PlaylistResponse(
    Long id,
    String name,
    String spotifyPlaylistId,
    Instant createdAt,
    /** Wersja do blokady optymistycznej — odeślij ją przy zmianie kolejności. */
    int version,
    List<PlaylistTrackResponse> tracks) {

    /**
     * {@code metrics} to komplet metryk wgranych z pliku, {@code null} dla
     * utworu, którego w żadnym pliku nie było. Planer liczy z nich ostrzeżenia
     * (głośność, metrum) i falowe tryby układania, którym zgrubne
     * {@code track.energy} nie wystarcza. Tonacja jedzie w {@code track.camelot}.
     */
    public record PlaylistTrackResponse(
        int position,
        String djSlot,
        String djSlotOverride,
        TrackMetricsResponse metrics,
        TrackResponse track) {

    }
}
