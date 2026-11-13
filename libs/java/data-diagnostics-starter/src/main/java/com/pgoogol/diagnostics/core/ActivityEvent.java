package com.pgoogol.diagnostics.core;

/**
 * Zdarzenie jednostki pracy, które nie jest pojedynczą operacją na magazynie: zakończona
 * transakcja i kolejne rodzaje dochodzące razem ze swoimi strategiami przechwytywania.
 * Analizy dostają je przez {@code AnalysisSession.onActivity} i wybierają swoje typy.
 */
public sealed interface ActivityEvent permits ConnectionEvent, PersistenceSessionEvent, RepositoryCallEvent, TransactionEvent {

}
