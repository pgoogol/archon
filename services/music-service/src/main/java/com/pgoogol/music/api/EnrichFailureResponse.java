package com.pgoogol.music.api;

import com.pgoogol.music.enrichment.EnrichmentFailure;

import java.time.Instant;

/**
 * Jeden utwór pominięty przez job wzbogacania razem z powodem.
 * Powód jest zdaniem dla człowieka, nie śladem stosu — stos zostaje w logu.
 */
public record EnrichFailureResponse(String spotifyId, String reason, Instant failedAt) {

    public static EnrichFailureResponse from(EnrichmentFailure failure) {

        return new EnrichFailureResponse(
            failure.spotifyId(), failure.reason(), failure.failedAt());
    }
}
