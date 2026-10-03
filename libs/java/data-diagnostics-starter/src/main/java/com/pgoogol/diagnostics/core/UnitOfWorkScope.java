package com.pgoogol.diagnostics.core;

/**
 * Otwarta granica jednostki pracy, zwracana przez {@link DiagnosticsEngine#open}.
 * Zamknięcie ostatniej granicy zamyka jednostkę i uruchamia raport.
 */
public interface UnitOfWorkScope extends AutoCloseable {

    /** Jednostka tej granicy; przy granicy zagnieżdżonej ta sama, co zewnętrznej. */
    UnitOfWork unit();

    /**
     * Bez wyjątku sprawdzanego, żeby try-with-resources nie wymagał {@code catch}.
     * Granicę zamyka się w wątku, który ją otworzył; drugie zamknięcie nic nie robi.
     */
    @Override
    void close();
}
