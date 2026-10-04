package com.pgoogol.testdiagnostics.spring.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OverrideConfig {

    @Bean
    Greeter greeter() {

        return () -> "hello";
    }
}
