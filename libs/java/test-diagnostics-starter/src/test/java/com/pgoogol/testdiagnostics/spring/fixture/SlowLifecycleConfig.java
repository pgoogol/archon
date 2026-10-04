package com.pgoogol.testdiagnostics.spring.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SlowLifecycleConfig {

    @Bean
    SlowLifecycle slowLifecycle() {

        return new SlowLifecycle();
    }
}
