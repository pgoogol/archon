package com.pgoogol.music.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pełny rekord katalogu — kontrakt Catalog API.
 *
 * <p>{@code camelot} nie jest kolumną: liczymy go z {@code musicalKey} przy
 * mapowaniu, tak samo jak {@code djSlot} w playliście liczymy z bpm
 * i energii.</p>
 */
public record TrackResponse(
    String spotifyId,
    String title,
    String artist,
    String album,
    Integer year,
    Integer durationMs,
    Integer popularity,
    Boolean explicit,
    String albumImageUrl,
    String isrc,
    String genreFamily,
    String style,
    Integer bpm,
    String bpmSource,
    BigDecimal danceability,
    String musicalKey,
    String camelot,
    String tempoClass,
    String energy,
    String lyricsTheme,
    String descriptionPl,
    String confidence,
    Instant enrichedAt,
    String modelUsed,
    Integer enrichVersion) {

}
