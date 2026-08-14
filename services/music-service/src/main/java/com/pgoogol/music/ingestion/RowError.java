package com.pgoogol.music.ingestion;

/**
 * Odrzucony wiersz CSV; {@code line} to numer wiersza danych (bez nagłówka).
 */
public record RowError(long line, String reason) {

}
