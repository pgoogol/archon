package com.pgoogol.llm.usage;

import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.autoconfigure.LlmPricingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CostEstimatorTest {

    @Test
    void estimate_whenPricingKnown_sumsAllFourCounters() {

        // given
        CostEstimator estimator = new CostEstimator(Map.of("test-model", pricing("15", "75", "1.5", "18.75")));
        LlmUsage usage = new LlmUsage(1_000_000, 1_000_000, 1_000_000, 1_000_000);

        // when
        Optional<BigDecimal> cost = estimator.estimate("test-model", usage);

        // then
        assertThat(cost).contains(new BigDecimal("110.250000"));
    }

    @Test
    void estimate_whenModelNameCarriesVersionSuffix_matchesByPrefix() {

        // given
        CostEstimator estimator = new CostEstimator(Map.of("test-model", pricing("10", "0", "0", "0")));

        // when
        Optional<BigDecimal> cost = estimator.estimate("test-model-20260101", LlmUsage.of(2_000_000, 0));

        // then
        assertThat(cost).contains(new BigDecimal("20.000000"));
    }

    @Test
    void estimate_whenTwoPrefixesMatch_takesTheLongerOne() {

        // given
        Map<String, LlmPricingProperties> pricing = Map.of(
                "test", pricing("1", "0", "0", "0"),
                "test-model", pricing("10", "0", "0", "0"));
        CostEstimator estimator = new CostEstimator(pricing);

        // when
        Optional<BigDecimal> cost = estimator.estimate("test-model-20260101", LlmUsage.of(1_000_000, 0));

        // then
        assertThat(cost).contains(new BigDecimal("10.000000"));
    }

    @Test
    void estimate_whenPricingUnknown_saysNothingInsteadOfZero() {

        // given
        CostEstimator estimator = new CostEstimator(Map.of());

        // when
        Optional<BigDecimal> cost = estimator.estimate("nieznany", LlmUsage.of(1000, 100));

        // then: zero w raporcie wyglądałoby jak darmowe zapytanie
        assertThat(cost).isEmpty();
    }

    @Test
    void estimate_whenModelUnknownBecauseProviderDidNotSayIt_saysNothing() {

        // given
        CostEstimator estimator = new CostEstimator(Map.of("test-model", pricing("10", "0", "0", "0")));

        // when
        Optional<BigDecimal> cost = estimator.estimate("", LlmUsage.of(1000, 100));

        // then
        assertThat(cost).isEmpty();
    }

    private LlmPricingProperties pricing(String input, String output, String cacheRead, String cacheWrite) {

        LlmPricingProperties pricing = new LlmPricingProperties();
        pricing.setInputPerMillion(new BigDecimal(input));
        pricing.setOutputPerMillion(new BigDecimal(output));
        pricing.setCacheReadPerMillion(new BigDecimal(cacheRead));
        pricing.setCacheWritePerMillion(new BigDecimal(cacheWrite));
        return pricing;
    }
}
