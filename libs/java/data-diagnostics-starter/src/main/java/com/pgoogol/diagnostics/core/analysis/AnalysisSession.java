package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;

/**
 * Stan jednej analizy w obrębie jednej jednostki pracy.
 *
 * <p>Silnik woła sesję pod blokadą jednostki, także gdy zdarzenia przychodzą z kilku
 * wątków, więc implementacja nie musi być bezpieczna wątkowo. Sesja, która rzuci
 * wyjątek, wypada z jednostki: jej stan jest już niepewny, więc jej wnioski też.</p>
 */
public interface AnalysisSession {

    /** Kolejna operacja jednostki. W prod to jedyna okazja, żeby ją policzyć. */
    void onEvent(DataAccessEvent event);

    /**
     * Zdarzenie inne niż operacja: zakończona transakcja, oddane połączenie i podobne.
     * Analiza wybiera typy, które ją obchodzą; domyślnie nic nie robi.
     */
    default void onActivity(ActivityEvent event) {
    }

    /** Wnioski po zamknięciu jednostki; pusta lista, gdy problemu nie ma. */
    List<Finding> findings();
}
