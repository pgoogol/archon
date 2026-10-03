package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWork;

import java.util.List;

/**
 * Wyjście wniosków: log, plik, metryki. Każde wyjście to osobna klasa za tym interfejsem.
 *
 * <p>Wyjątek z reportera nie wychodzi poza silnik i nie zabiera wyników pozostałym
 * reporterom, ale też nie jest nigdzie ponawiany — reporter, który może zawieść
 * (plik, sieć), sam decyduje, co wtedy zrobić.</p>
 */
public interface FindingReporter {

    /**
     * Raport z zamkniętej jednostki. Przychodzi też wtedy, gdy wniosków nie ma, bo samo
     * podsumowanie jednostki (liczba operacji, czas) bywa potrzebne.
     */
    void report(UnitOfWork unit, List<Finding> findings);
}
