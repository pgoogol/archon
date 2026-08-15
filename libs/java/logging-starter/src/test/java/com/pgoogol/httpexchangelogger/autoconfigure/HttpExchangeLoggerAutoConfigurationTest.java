package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.factory.HttpExchangeLogEventFactory;
import com.pgoogol.httpexchangelogger.filter.HttpExchangeLoggingFilter;
import com.pgoogol.httpexchangelogger.resolver.EndpointLoggingModeResolver;
import com.pgoogol.httpexchangelogger.sanitizer.BodySanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.HeaderSanitizer;
import com.pgoogol.httpexchangelogger.serialization.HttpExchangeLogEventJsonWriter;
import com.pgoogol.httpexchangelogger.support.ClientIpExtractor;
import com.pgoogol.httpexchangelogger.support.RequestIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeLoggerAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpExchangeLoggerAutoConfiguration.class));

    @Test
    void autoConfiguration_whenDefaults_registersAllExpectedBeans() {

        // given / when / then
        webRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(HttpExchangeLoggerProperties.class);
            assertThat(ctx).hasSingleBean(EndpointLoggingModeResolver.class);
            assertThat(ctx).hasSingleBean(BodySanitizer.class);
            assertThat(ctx).hasSingleBean(HeaderSanitizer.class);
            assertThat(ctx).hasSingleBean(RequestIdProvider.class);
            assertThat(ctx).hasSingleBean(ClientIpExtractor.class);
            assertThat(ctx).hasSingleBean(HttpExchangeLogEventJsonWriter.class);
            assertThat(ctx).hasSingleBean(HttpExchangeLogEventFactory.class);
            assertThat(ctx).hasSingleBean(HttpExchangeLoggingFilter.class);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void autoConfiguration_whenDefaults_registersFilterViaFilterRegistrationBeanWithOrder() {

        // given / when / then
        webRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);
            FilterRegistrationBean<HttpExchangeLoggingFilter> registration =
                    ctx.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(HttpExchangeLoggingFilter.class);
            assertThat(registration.getUrlPatterns()).contains("/*");
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
        });
    }

    @Test
    void autoConfiguration_whenFilterOrderPropertySet_usesConfiguredOrder() {

        // given
        webRunner.withPropertyValues("http-exchange-logger.filter-order=42")
                // when
                .run(ctx -> {
                    // then
                    @SuppressWarnings("unchecked")
                    FilterRegistrationBean<HttpExchangeLoggingFilter> registration =
                            ctx.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(42);
                });
    }

    @Test
    void autoConfiguration_whenNonWebApplication_doesNotRegisterFilter() {

        // given / when / then
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HttpExchangeLoggerAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(HttpExchangeLoggingFilter.class));
    }

}
