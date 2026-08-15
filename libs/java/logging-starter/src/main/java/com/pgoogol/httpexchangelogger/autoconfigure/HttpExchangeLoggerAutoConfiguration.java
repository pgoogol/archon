package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.factory.HttpExchangeLogEventFactory;
import com.pgoogol.httpexchangelogger.filter.HttpExchangeLoggingFilter;
import com.pgoogol.httpexchangelogger.resolver.EndpointLoggingModeResolver;
import com.pgoogol.httpexchangelogger.resolver.ExchangeSampler;
import com.pgoogol.httpexchangelogger.runtime.RuntimeModeOverrideManager;
import com.pgoogol.httpexchangelogger.sanitizer.BodySanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.DefaultBodySanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.DefaultHeaderSanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.FormBodySanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.HeaderSanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.JsonBodySanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.SensitiveValueMasker;
import com.pgoogol.httpexchangelogger.sanitizer.XmlBodySanitizer;
import com.pgoogol.httpexchangelogger.serialization.HttpExchangeLogEventJsonWriter;
import com.pgoogol.httpexchangelogger.support.ClientIpExtractor;
import com.pgoogol.httpexchangelogger.support.DefaultClientIpExtractor;
import com.pgoogol.httpexchangelogger.support.DefaultRequestIdProvider;
import com.pgoogol.httpexchangelogger.support.MdcTraceContextProvider;
import com.pgoogol.httpexchangelogger.support.RequestIdProvider;
import com.pgoogol.httpexchangelogger.support.TraceContextProvider;
import com.pgoogol.httpexchangelogger.tracing.HttpExchangeSpanEnricher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@EnableConfigurationProperties(HttpExchangeLoggerProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
public class HttpExchangeLoggerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EndpointLoggingModeResolver endpointLoggingModeResolver(HttpExchangeLoggerProperties properties) {

        return new EndpointLoggingModeResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExchangeSampler exchangeSampler(HttpExchangeLoggerProperties properties) {

        return new ExchangeSampler(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeModeOverrideManager runtimeModeOverrideManager(HttpExchangeLoggerProperties properties) {

        return new RuntimeModeOverrideManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SensitiveValueMasker sensitiveValueMasker(HttpExchangeLoggerProperties properties) {

        return new SensitiveValueMasker(properties.getMask().getFields());
    }

    @Bean
    @ConditionalOnMissingBean
    public HeaderSanitizer headerSanitizer(SensitiveValueMasker masker,
                                           HttpExchangeLoggerProperties properties) {

        return new DefaultHeaderSanitizer(masker, properties.getMask());
    }

    @Bean
    @ConditionalOnMissingBean
    public BodySanitizer bodySanitizer(SensitiveValueMasker masker,
                                       HttpExchangeLoggerProperties properties,
                                       ObjectProvider<ObjectMapper> objectMapperProvider) {

        HttpExchangeLoggerProperties.Mask maskProperties = properties.getMask();
        JsonBodySanitizer json = new JsonBodySanitizer(resolveObjectMapper(objectMapperProvider), masker, maskProperties);
        XmlBodySanitizer xml = new XmlBodySanitizer(masker, maskProperties);
        FormBodySanitizer form = new FormBodySanitizer(masker, maskProperties);
        return new DefaultBodySanitizer(json, xml, form);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestIdProvider requestIdProvider() {

        return new DefaultRequestIdProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientIpExtractor clientIpExtractor() {

        return new DefaultClientIpExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceContextProvider traceContextProvider() {

        return new MdcTraceContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpExchangeLogEventJsonWriter httpExchangeLogEventJsonWriter(ObjectProvider<ObjectMapper> objectMapperProvider) {

        return new HttpExchangeLogEventJsonWriter(resolveObjectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpExchangeLogEventFactory httpExchangeLogEventFactory(HttpExchangeLoggerProperties properties,
                                                                   HeaderSanitizer headerSanitizer,
                                                                   BodySanitizer bodySanitizer,
                                                                   ClientIpExtractor clientIpExtractor,
                                                                   ObjectProvider<ObjectMapper> objectMapperProvider,
                                                                   TraceContextProvider traceContextProvider) {

        return new HttpExchangeLogEventFactory(properties, headerSanitizer, bodySanitizer,
                clientIpExtractor, resolveObjectMapper(objectMapperProvider), traceContextProvider);
    }

    private static ObjectMapper resolveObjectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {

        return objectMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpExchangeLoggingFilter httpExchangeLoggingFilter(HttpExchangeLoggerProperties properties,
                                                               EndpointLoggingModeResolver modeResolver,
                                                               HttpExchangeLogEventFactory eventFactory,
                                                               HttpExchangeLogEventJsonWriter jsonWriter,
                                                               RequestIdProvider requestIdProvider,
                                                               ExchangeSampler sampler,
                                                               RuntimeModeOverrideManager overrideManager,
                                                               ObjectProvider<HttpExchangeSpanEnricher> spanEnrichers) {

        return new HttpExchangeLoggingFilter(properties, modeResolver, eventFactory, jsonWriter,
                requestIdProvider, sampler, overrideManager, spanEnrichers.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(name = "httpExchangeLoggingFilterRegistration")
    public FilterRegistrationBean<HttpExchangeLoggingFilter> httpExchangeLoggingFilterRegistration(
            HttpExchangeLoggingFilter filter,
            HttpExchangeLoggerProperties properties) {

        FilterRegistrationBean<HttpExchangeLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("httpExchangeLoggingFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getFilterOrder());
        return registration;
    }

}
