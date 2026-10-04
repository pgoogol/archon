package com.pgoogol.testdiagnostics.spring.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Inna konfiguracja: przejście na nią wstrzymuje kontekst {@link SlowLifecycleConfig}. */
@Configuration(proxyBeanMethods = false)
public class OtherConfig {

    @Bean
    String otherGreeting() {

        return "other";
    }
}
