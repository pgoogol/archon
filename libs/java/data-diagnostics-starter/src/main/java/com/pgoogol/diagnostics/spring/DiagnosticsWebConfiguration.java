package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerMapping;

/** Granica HTTP dla aplikacji servletowej: filtr otwierający jednostkę na żądanie. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HandlerMapping.class)
class DiagnosticsWebConfiguration {

    /** Klasa zagnieżdżona idzie przed metodami klasy zewnętrznej, więc MDC jest tylko zapasem. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer.class)
    static class TracingTraceIdConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TraceIdProvider traceIdProvider(ObjectProvider<Tracer> tracer) {

            return new MicrometerTraceIdProvider(tracer::getIfAvailable, new MdcTraceIdProvider());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    TraceIdProvider traceIdProvider() {

        return new MdcTraceIdProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    HttpRequestBoundary httpRequestBoundary(DiagnosticsEngine engine, TraceIdProvider traceIds) {

        return new HttpRequestBoundary(engine, traceIds);
    }

    @Bean
    @ConditionalOnMissingBean(name = "dataDiagnosticsHttpBoundaryRegistration")
    FilterRegistrationBean<HttpRequestBoundary> dataDiagnosticsHttpBoundaryRegistration(HttpRequestBoundary boundary) {

        FilterRegistrationBean<HttpRequestBoundary> registration = new FilterRegistrationBean<>(boundary);
        registration.setName("dataDiagnosticsHttpBoundary");
        registration.addUrlPatterns("/*");
        registration.setOrder(HttpRequestBoundary.ORDER);
        return registration;
    }
}
