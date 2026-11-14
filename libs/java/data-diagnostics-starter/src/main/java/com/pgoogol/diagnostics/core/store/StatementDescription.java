package com.pgoogol.diagnostics.core.store;

import com.pgoogol.diagnostics.core.OperationKind;

import java.util.Objects;

/**
 * Kształt i rodzaj jednej operacji, policzone razem.
 *
 * @param shape tekst bez wartości, po którym analizy grupują wykonania
 * @param kind  odczyt, zapis albo inna
 */
public record StatementDescription(String shape, OperationKind kind) {

    public StatementDescription {

        Objects.requireNonNull(shape, "kształt operacji jest wymagany");
        Objects.requireNonNull(kind, "rodzaj operacji jest wymagany");
    }
}
