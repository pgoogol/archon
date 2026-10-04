package com.pgoogol.testdiagnostics.spring.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Konfiguracja, której kontekst nie wstaje: bean rzuca przy tworzeniu. */
@Configuration(proxyBeanMethods = false)
public class BrokenConfig {

    @Bean
    String brokenBean() {

        throw new IllegalStateException("bean broke");
    }
}
