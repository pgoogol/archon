package com.pgoogol.diagnostics.core;

/**
 * Granica jednostki pracy: miejsce, w którym aplikacja zaczyna i kończy kawałek pracy
 * wart osobnego raportu (żądanie HTTP, zadanie harmonogramu, komunikat, chunk). Każda
 * granica to osobna klasa, która otwiera jednostkę swojego typu przez
 * {@link DiagnosticsEngine#open} i zamyka ją w tym samym wątku.
 */
public interface UnitOfWorkBoundary {

    /** Typ jednostek, które otwiera ta granica. */
    UnitOfWorkType type();
}
