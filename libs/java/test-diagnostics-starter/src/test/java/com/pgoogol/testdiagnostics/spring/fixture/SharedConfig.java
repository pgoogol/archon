package com.pgoogol.testdiagnostics.spring.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Konfiguracja wspólna dla dwóch klas-wzorców: obie powinny dostać jedno środowisko. */
@Configuration(proxyBeanMethods = false)
public class SharedConfig {

    @Bean
    String sharedGreeting() {

        return "hello";
    }
}
