package com.pgoogol.music.ingestion;

/**
 * Wynik importu jednego pliku z metrykami w partii. Plik albo wszedł
 * (z własnym raportem wierszy), albo padł w całości — np. na nagłówku bez
 * kolumny identyfikującej utwór. Pozostałe pliki partii idą niezależnie.
 */
public sealed interface MetricsFileReport {

    String file();

    record Imported(String file, MetricsIngestReport report) implements MetricsFileReport {

    }

    record Failed(String file, String errorCode, String reason) implements MetricsFileReport {

    }
}
