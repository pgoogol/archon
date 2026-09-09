package com.pgoogol.llm.usage;

import com.pgoogol.llm.LlmResponse;
import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.StopReason;
import com.pgoogol.llm.autoconfigure.LlmPricingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LlmUsagePublisherTest {

    private static final LlmResponse RESPONSE =
            new LlmResponse("tekst", StopReason.COMPLETED, new LlmUsage(1_000_000, 0, 0, 0), "test-model");

    @Test
    void publish_whenListenerRegistered_deliversUsageWithEstimatedCost() {

        // given
        List<LlmUsageEvent> received = new ArrayList<>();
        LlmUsagePublisher publisher = new LlmUsagePublisher(List.of(received::add), estimator());

        // when
        publisher.publish("text", RESPONSE, Duration.ofMillis(250));

        // then
        assertThat(received).singleElement().satisfies(event -> {

            assertThat(event.clientName()).isEqualTo("text");
            assertThat(event.model()).isEqualTo("test-model");
            assertThat(event.usage().totalTokens()).isEqualTo(1_000_000);
            assertThat(event.estimatedCost()).contains(new BigDecimal("3.000000"));
            assertThat(event.duration()).isEqualTo(Duration.ofMillis(250));
        });
    }

    @Test
    void publish_whenListenerThrows_doesNotBreakTheAnsweredCall() {

        // given: odpowiedź jest już opłacona, nieudany zapis rozliczenia jej nie unieważnia
        List<LlmUsageEvent> received = new ArrayList<>();
        LlmUsageListener failing = event -> {

            throw new IllegalStateException("baza padła");
        };
        LlmUsagePublisher publisher = new LlmUsagePublisher(List.of(failing, received::add), estimator());

        // when, then
        assertThatCode(() -> publisher.publish("text", RESPONSE, Duration.ZERO)).doesNotThrowAnyException();
        assertThat(received).hasSize(1);
    }

    private CostEstimator estimator() {

        LlmPricingProperties pricing = new LlmPricingProperties();
        pricing.setInputPerMillion(new BigDecimal("3"));
        return new CostEstimator(Map.of("test-model", pricing));
    }
}
