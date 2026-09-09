package com.pgoogol.llm.usage;

import com.pgoogol.llm.LlmUsage;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Co kosztowało jedno wywołanie. Koszt jest pusty, gdy cennik nie zna modelu —
 * lepiej „nie wiem" niż zero, które w raporcie wygląda jak darmowe zapytanie.
 */
public record LlmUsageEvent(String clientName, String model, LlmUsage usage,
                            Optional<BigDecimal> estimatedCost, Duration duration) {

    public LlmUsageEvent {

        Objects.requireNonNull(clientName, "clientName");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        Objects.requireNonNull(duration, "duration");
    }
}
