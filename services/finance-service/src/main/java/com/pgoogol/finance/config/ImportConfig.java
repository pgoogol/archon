package com.pgoogol.finance.config;

import com.pgoogol.finance.imports.statement.AmountParser;
import com.pgoogol.finance.imports.statement.DedupKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beany modelu parsowania wyciągów.
 *
 * <p>Klasy z {@code imports.statement} nie noszą adnotacji Springa świadomie —
 * to ich jedyne miejsce spotkania z kontekstem aplikacji. Dzięki temu parsowanie
 * kwot i klucz deduplikacji da się testować jak zwykły kod, bez kontekstu
 * i bez bazy, a {@code ArchitectureTest} może tego pilnować.</p>
 */
@Configuration
public class ImportConfig {

    @Bean
    public AmountParser amountParser() {

        return new AmountParser();
    }

    @Bean
    public DedupKey dedupKey() {

        return new DedupKey();
    }
}
