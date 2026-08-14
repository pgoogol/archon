package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.admin.HttpExchangeLoggerEndpoint;
import com.pgoogol.httpexchangelogger.runtime.RuntimeModeOverrideManager;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the admin actuator endpoint when Spring Boot Actuator is on the
 * classpath. The endpoint follows the standard actuator exposure rules, so it
 * stays unavailable until explicitly included via
 * {@code management.endpoints.web.exposure.include}.
 */
@AutoConfiguration(after = HttpExchangeLoggerAutoConfiguration.class)
@ConditionalOnClass({Endpoint.class, ConditionalOnAvailableEndpoint.class})
@ConditionalOnBean(RuntimeModeOverrideManager.class)
public class HttpExchangeLoggerAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public HttpExchangeLoggerEndpoint httpExchangeLoggerEndpoint(HttpExchangeLoggerProperties properties,
                                                                 RuntimeModeOverrideManager overrideManager) {

        return new HttpExchangeLoggerEndpoint(properties, overrideManager);
    }

}
