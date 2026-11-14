package com.pgoogol.diagnostics.jdbc;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Objects;

/**
 * Atrapa {@code DataSource} do pomiarów: każde wywołanie JDBC wraca natychmiast, bez sieci
 * i bez Mockito, którego własny koszt byłby większy niż to, co mierzymy.
 */
final class InstantDataSource extends AbstractDataSource {

    private static final Map<Class<?>, Object> DEFAULTS = Map.of(boolean.class, false, int.class, 0, long.class, 0L);

    private final ResultSet resultSet = stub(ResultSet.class);

    private final PreparedStatement statement = stub(PreparedStatement.class);

    private final Connection connection = stub(Connection.class);

    @Override
    public Connection getConnection() {

        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) {

        return connection;
    }

    private <T> T stub(Class<T> type) {

        InvocationHandler handler = (proxy, method, arguments) -> answer(method);
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        return type.cast(instance);
    }

    /** Zapytanie daje wspólną atrapę wyniku, reszta wartość domyślną swojego typu. */
    private @Nullable Object answer(Method method) {

        Class<?> returned = method.getReturnType();
        if (Objects.equals(returned, PreparedStatement.class)) {

            return statement;
        }
        if (Objects.equals(returned, ResultSet.class)) {

            return resultSet;
        }
        return DEFAULTS.get(returned);
    }
}
