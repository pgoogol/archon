package com.pgoogol.music.ingestion;

import java.util.List;

public record CsvParseResult(List<ParsedTrack> tracks, List<RowError> errors) {

}
