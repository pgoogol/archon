package com.pgoogol.diagnostics.jdbc;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Owija każdy bean {@link DataSource} tak, żeby jego połączenia przechodziły przez
 * datasource-proxy i trafiały do {@link JdbcCaptureStrategy}.
 *
 * <p>Zamiast podmieniać bean na {@code ProxyDataSource}, który zmienia typ i psuje
 * wstrzykiwanie po {@code HikariDataSource}, robimy podklasę klasy beana przez
 * {@link ProxyFactory} z {@code proxyTargetClass=true}. Proxy przechwytuje tylko
 * {@code getConnection} i oddaje połączenie z datasource-proxy; resztę wywołań dostaje
 * oryginał. Klasa finalna dostaje proxy po interfejsach i wtedy wstrzykiwanie po klasie
 * przestaje działać, bo inaczej się nie da.</p>
 *
 * <p>Pomijamy bean już owinięty ({@link CapturedDataSource}) i {@link DelegatingDataSource}:
 * ten deleguje do innego {@code DataSource}, zwykle też beana i też owiniętego, więc
 * każde zapytanie liczyłoby się dwa razy.</p>
 */
public class DataSourceCapturePostProcessor implements BeanPostProcessor {

    private final ObjectProvider<JdbcCaptureStrategy> strategyProvider;

    public DataSourceCapturePostProcessor(ObjectProvider<JdbcCaptureStrategy> strategyProvider) {

        this.strategyProvider = Objects.requireNonNull(strategyProvider, "dostawca strategii JDBC jest wymagany");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {

        if (!(bean instanceof DataSource dataSource) || skipped(bean)) {

            return bean;
        }
        return wrap(dataSource, beanName);
    }

    /** Owija {@code DataSource}; publiczne dla testów i dla aplikacji, które składają go ręcznie. */
    public DataSource wrap(DataSource original, String name) {

        DeferredQueryListener listener = new DeferredQueryListener(strategyProvider);
        ProxyDataSource capturing = ProxyDataSourceBuilder.create(name, original)
            .listener(listener)
            .build();
        Class<?> type = original.getClass();
        ProxyFactory factory = new ProxyFactory(original);
        factory.setProxyTargetClass(!Modifier.isFinal(type.getModifiers()));
        factory.addInterface(CapturedDataSource.class);
        factory.addAdvice(new ConnectionInterceptor(capturing));
        return (DataSource) factory.getProxy(type.getClassLoader());
    }

    private boolean skipped(Object bean) {

        return bean instanceof CapturedDataSource || bean instanceof DelegatingDataSource;
    }

    /** Podmienia połączenia na te z datasource-proxy; każde inne wywołanie idzie do oryginału. */
    private record ConnectionInterceptor(ProxyDataSource capturing) implements MethodInterceptor {

        @Override
        public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {

            String method = invocation.getMethod().getName();
            if (!Objects.equals(method, "getConnection")) {

                return invocation.proceed();
            }
            Object[] arguments = invocation.getArguments();
            if (arguments.length == 2) {

                return capturing.getConnection((String) arguments[0], (String) arguments[1]);
            }
            return capturing.getConnection();
        }
    }
}
