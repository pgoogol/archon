package com.pgoogol.llm.usage;

import com.pgoogol.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Rozsyła zdarzenia zużycia do słuchaczy serwisu. Wyjątek słuchacza jest
 * łapany: nieudany zapis rozliczenia nie może przewrócić odpowiedzi, za którą
 * już zapłaciliśmy.
 */
public class LlmUsagePublisher {

    private static final Logger log = LoggerFactory.getLogger(LlmUsagePublisher.class);

    private final List<LlmUsageListener> listeners;
    private final CostEstimator costEstimator;

    public LlmUsagePublisher(List<LlmUsageListener> listeners, CostEstimator costEstimator) {

        this.listeners = Objects.requireNonNullElse(listeners, List.of());
        this.costEstimator = Objects.requireNonNull(costEstimator, "costEstimator");
    }

    public void publish(String clientName, LlmResponse response, Duration duration) {

        Objects.requireNonNull(response, "response");
        Optional<BigDecimal> cost = costEstimator.estimate(response.model(), response.usage());
        LlmUsageEvent event = new LlmUsageEvent(clientName, response.model(), response.usage(), cost, duration);
        log.debug("Klient LLM {} zużył {} tokenów w {} ms", clientName, response.usage().totalTokens(),
                duration.toMillis());
        listeners.forEach(listener -> notifyListener(listener, event));
    }

    private void notifyListener(LlmUsageListener listener, LlmUsageEvent event) {

        try {

            listener.onUsage(event);
        } catch (RuntimeException ex) {

            log.warn("Słuchacz zużycia LLM {} rzucił wyjątkiem", listener.getClass().getName(), ex);
        }
    }
}
