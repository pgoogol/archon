package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.support.MdcTraceContextProvider;
import com.pgoogol.httpexchangelogger.support.TraceContextProvider;
import com.pgoogol.httpexchangelogger.tracing.MicrometerTraceContextProvider;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Provides a Micrometer Tracing backed {@link TraceContextProvider} when
 * micrometer-tracing is on the classpath. Runs before the main
 * auto-configuration so its MDC-based default backs off. The tracer bean is
 * resolved lazily and the provider falls back to the MDC when no tracer or no
 * current span is available.
 */
@AutoConfiguration(before = HttpExchangeLoggerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Tracer.class)
public class HttpExchangeLoggerTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceContextProvider.class)
    public TraceContextProvider micrometerTraceContextProvider(ObjectProvider<Tracer> tracerProvider) {

        return new MicrometerTraceContextProvider(tracerProvider::getIfAvailable, new MdcTraceContextProvider());
    }

}
