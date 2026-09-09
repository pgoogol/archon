package com.pgoogol.llm.usage;

import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.autoconfigure.LlmPricingProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Szacunek kosztu wywołania z cennika w konfiguracji. Szacunek, nie rachunek:
 * provider potrafi naliczyć inaczej (rabaty, batch), a nazwy modeli zmieniają
 * się częściej niż wpisy w cenniku.
 */
public class CostEstimator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int SCALE = 6;

    private final Map<String, LlmPricingProperties> pricing;

    public CostEstimator(Map<String, LlmPricingProperties> pricing) {

        this.pricing = Objects.requireNonNullElse(pricing, Map.of());
    }

    /**
     * @return pusty, gdy cennik nie ma wpisu pasującego do modelu
     */
    public Optional<BigDecimal> estimate(String model, LlmUsage usage) {

        Objects.requireNonNull(usage, "usage");
        Optional<LlmPricingProperties> matched = match(model);
        return matched.map(prices -> total(prices, usage));
    }

    private Optional<LlmPricingProperties> match(String model) {

        if (Objects.isNull(model) || model.isBlank()) {

            return Optional.empty();
        }
        return pricing.entrySet().stream()
                .filter(entry -> model.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue);
    }

    private BigDecimal total(LlmPricingProperties prices, LlmUsage usage) {

        BigDecimal input = cost(prices.getInputPerMillion(), usage.inputTokens());
        BigDecimal output = cost(prices.getOutputPerMillion(), usage.outputTokens());
        BigDecimal cacheRead = cost(prices.getCacheReadPerMillion(), usage.cacheReadTokens());
        BigDecimal cacheWrite = cost(prices.getCacheWritePerMillion(), usage.cacheWriteTokens());
        return input.add(output).add(cacheRead).add(cacheWrite).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal cost(BigDecimal perMillion, long tokens) {

        BigDecimal rate = Objects.requireNonNullElse(perMillion, BigDecimal.ZERO);
        BigDecimal count = BigDecimal.valueOf(tokens);
        return rate.multiply(count).divide(MILLION, SCALE, RoundingMode.HALF_UP);
    }
}
