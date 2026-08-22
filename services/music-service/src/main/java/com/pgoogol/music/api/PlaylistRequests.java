package com.pgoogol.music.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Zlecenia zmian playlisty — kontrakty {@code /music/api/v1/playlists*}.
 *
 * <p>{@code version} jedzie w ciele, nie w nagłówku {@code If-Match}:
 * front trzyma cały obiekt w stanie widoku, więc wersja jedzie z nim za darmo,
 * a ETagi wymagałyby osobnego magazynu obok stanu.</p>
 */
public final class PlaylistRequests {

    private PlaylistRequests() {

    }

    /** {@code version} wymagana przy zmianie nazwy istniejącego setu. */
    public record SavePlaylistRequest(@NotBlank @Size(max = 255) String name, Integer version) {

    }

    public record AddPlaylistTrackRequest(@NotBlank @Size(max = 64) String spotifyId) {

    }

    public record ReorderPlaylistRequest(@NotEmpty List<@NotBlank String> spotifyIds,
                                        @NotNull Integer version) {

    }
}
