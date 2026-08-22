package com.pgoogol.finance.report;

import org.springframework.lang.Nullable;

/**
 * @param monthlyAverageMinor średnia miesięczna z dwunastu miesięcy przed okresem
 * @param changePercent       zmiana wobec poprzedniego okresu jako tekst;
 *                            {@code null}, gdy poprzedni okres był zerowy —
 *                            dzielenie przez zero nie jest wzrostem o nieskończoność
 */
public record CategoryComparisonRow(
    long categoryId,
    String categoryName,
    long currentMinor,
    long previousMinor,
    long monthlyAverageMinor,
    @Nullable String changePercent) {

}
