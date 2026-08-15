package com.pgoogol.music.ingestion;

import java.util.List;

public record MetricsParseResult(List<ParsedMetrics> rows, List<RowError> errors) {

}
