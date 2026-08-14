package com.pgoogol.music.api;

import com.pgoogol.music.enrichment.spotify.SpotifyAccountStatus;
import org.springframework.stereotype.Component;

@Component
public class SpotifyAccountApiMapper {

    public SpotifyAccountResponse toResponse(SpotifyAccountStatus status) {

        return new SpotifyAccountResponse(status.connected(), status.spotifyUserId(),
            status.displayName(), status.scopes(), status.expiresAt(), status.connectedAt());
    }
}
