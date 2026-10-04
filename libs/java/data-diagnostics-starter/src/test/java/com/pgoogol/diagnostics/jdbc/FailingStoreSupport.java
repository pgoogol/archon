package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.store.DataStoreSupport;

/** Strategia bazy testowa, która psuje się przy liczeniu kształtu. */
final class FailingStoreSupport implements DataStoreSupport {

    @Override
    public DataStore store() {

        return new DataStore("postgresql");
    }

    @Override
    public String shape(String statement) {

        throw new IllegalStateException("awaria strategii bazy");
    }

    @Override
    public OperationKind classify(String statement) {

        return OperationKind.OTHER;
    }
}
