package com.pgoogol.finance.config;

import com.pgoogol.finance.recurring.schedule.OccurrenceSchedule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean arytmetyki terminów.
 *
 * <p>{@code recurring.schedule} nie nosi adnotacji Springa świadomie — liczenie
 * dat ma dać się sprawdzić bez kontekstu i bez bazy, a {@code ArchitectureTest}
 * tego pilnuje. To jedyne miejsce, w którym ta klasa spotyka się z kontenerem.</p>
 */
@Configuration
public class RecurringConfig {

    @Bean
    public OccurrenceSchedule occurrenceSchedule() {

        return new OccurrenceSchedule();
    }
}
