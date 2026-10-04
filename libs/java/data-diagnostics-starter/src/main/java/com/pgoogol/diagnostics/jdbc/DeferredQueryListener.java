package com.pgoogol.diagnostics.jdbc;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Objects;

/**
 * Słuchacz, który sięga po {@link JdbcCaptureStrategy} dopiero przy pierwszym zapytaniu.
 * Post-processor owija {@code DataSource} wcześnie, a strategia potrzebuje silnika, analiz
 * i ustawień; pobranie jej od razu wymuszałoby ich budowę w trakcie tworzenia
 * {@code DataSource}. Dopóki strategii nie ma, zapytania przechodzą bez zapisu.
 */
final class DeferredQueryListener implements QueryExecutionListener {

    private final ObjectProvider<JdbcCaptureStrategy> strategyProvider;

    private volatile @Nullable QueryExecutionListener resolved;

    DeferredQueryListener(ObjectProvider<JdbcCaptureStrategy> strategyProvider) {

        this.strategyProvider = strategyProvider;
    }

    @Override
    public void beforeQuery(ExecutionInfo execution, List<QueryInfo> queries) {

        QueryExecutionListener listener = listener();
        listener.beforeQuery(execution, queries);
    }

    @Override
    public void afterQuery(ExecutionInfo execution, List<QueryInfo> queries) {

        QueryExecutionListener listener = listener();
        listener.afterQuery(execution, queries);
    }

    private QueryExecutionListener listener() {

        QueryExecutionListener current = resolved;
        if (Objects.nonNull(current)) {

            return current;
        }
        JdbcCaptureStrategy strategy = strategyProvider.getIfAvailable();
        if (Objects.isNull(strategy)) {

            return QueryExecutionListener.DEFAULT;
        }
        resolved = strategy;
        return strategy;
    }
}
