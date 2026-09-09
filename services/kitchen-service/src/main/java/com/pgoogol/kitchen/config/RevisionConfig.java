package com.pgoogol.kitchen.config;

import com.pgoogol.kitchen.revision.diff.RecipeDiffer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rachunek na dzienniku zmian nie zna Springa — nie ma na sobie adnotacji i nie
 * daje się znaleźć skanowaniem. Beana robimy tutaj: pakiet
 * {@code revision.diff} ma zostać czystą logiką, którą testuje się bez kontekstu.
 */
@Configuration
public class RevisionConfig {

    @Bean
    public RecipeDiffer recipeDiffer() {

        return new RecipeDiffer();
    }
}
