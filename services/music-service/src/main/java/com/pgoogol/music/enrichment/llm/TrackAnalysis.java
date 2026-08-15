package com.pgoogol.music.enrichment.llm;

import com.pgoogol.music.catalog.GenreFamily;
import org.springframework.lang.Nullable;

/**
 * Strukturalny wynik analizy AI jednego utworu (grupa AI);
 * {@code bpmEstimate} tylko gdy jawnie zażądano (kaskada M1.4 pusta).
 */
public record TrackAnalysis(
    String spotifyId,
    String style,
    GenreFamily genreFamily,
    String lyricsTheme,
    String descriptionPl,
    String energy,
    String confidence,
    @Nullable Integer bpmEstimate) {

}
