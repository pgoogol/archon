package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.UnitOfWork;

/**
 * Analiza jednostki pracy. Każda analiza to osobna klasa za tym interfejsem, a silnik
 * zna tylko interfejs.
 *
 * <p>Na każdą jednostkę analiza zaczyna świeżą sesję, więc stan jednej jednostki nie
 * przecieka do następnej, a sama analiza może być bezstanowa i współdzielona.</p>
 */
public interface DiagnosticAnalyzer {

    /** Stały identyfikator, np. {@code n-plus-one}; po nim analizę się włącza i ustawia progi. */
    String id();

    /** Sesja analizy dla jednej jednostki pracy. */
    AnalysisSession start(UnitOfWork unit);
}
