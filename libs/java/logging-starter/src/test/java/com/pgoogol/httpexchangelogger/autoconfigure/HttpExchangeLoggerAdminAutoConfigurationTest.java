package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.admin.HttpExchangeLoggerEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeLoggerAdminAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpExchangeLoggerAutoConfiguration.class,
                    HttpExchangeLoggerAdminAutoConfiguration.class));

    @Test
    void adminAutoConfiguration_whenEndpointExposed_registersEndpoint() {

        // given
        webRunner.withPropertyValues("management.endpoints.web.exposure.include=httpexchangelogger"
                // when
        ).run(ctx ->
                // then
                assertThat(ctx).hasSingleBean(HttpExchangeLoggerEndpoint.class));
    }

    @Test
    void adminAutoConfiguration_whenEndpointNotExposed_doesNotRegisterEndpoint() {

        // given no exposure configuration / when
        webRunner.run(ctx ->
                // then the endpoint stays unavailable by default
                assertThat(ctx).doesNotHaveBean(HttpExchangeLoggerEndpoint.class));
    }

    @Test
    void adminAutoConfiguration_whenActuatorMissingFromClasspath_backsOff() {

        // given
        webRunner.withClassLoader(new FilteredClassLoader(Endpoint.class))
                .withPropertyValues("management.endpoints.web.exposure.include=httpexchangelogger")
                // when
                .run(ctx ->
                        // then
                        assertThat(ctx).doesNotHaveBean(HttpExchangeLoggerEndpoint.class));
    }

}
