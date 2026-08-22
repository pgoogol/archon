package com.pgoogol.finance.config;

import com.pgoogol.finance.categorization.match.ScheduleMatcher;
import com.pgoogol.finance.categorization.match.TextMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beany dopasowywania.
 *
 * <p>{@code categorization.match} nie nosi adnotacji Springa świadomie —
 * reguła „kwota o 5% wyższa i termin trzy dni wcześniej" ma dać się sprawdzić
 * przez podanie liczb, a nie przez postawienie kontekstu i bazy. Pilnuje tego
 * {@code ArchitectureTest}.</p>
 */
@Configuration
public class CategorizationConfig {

    @Bean
    public TextMatcher textMatcher() {

        return new TextMatcher();
    }

    @Bean
    public ScheduleMatcher scheduleMatcher(TextMatcher textMatcher) {

        return new ScheduleMatcher(textMatcher);
    }
}
