package com.pgoogol.llm.autoconfigure;

import java.math.BigDecimal;

/**
 * Cennik jednego modelu, w walucie, którą wybierze serwis — starter tylko mnoży,
 * nie przelicza kursów. Wartości podawane za milion tokenów, bo tak podają je
 * providerzy i tak da się je przepisać z ich cennika bez rachunków w głowie.
 */
public class LlmPricingProperties {

    private BigDecimal inputPerMillion = BigDecimal.ZERO;

    private BigDecimal outputPerMillion = BigDecimal.ZERO;

    private BigDecimal cacheReadPerMillion = BigDecimal.ZERO;

    private BigDecimal cacheWritePerMillion = BigDecimal.ZERO;

    public BigDecimal getInputPerMillion() {

        return inputPerMillion;
    }

    public void setInputPerMillion(BigDecimal inputPerMillion) {

        this.inputPerMillion = inputPerMillion;
    }

    public BigDecimal getOutputPerMillion() {

        return outputPerMillion;
    }

    public void setOutputPerMillion(BigDecimal outputPerMillion) {

        this.outputPerMillion = outputPerMillion;
    }

    public BigDecimal getCacheReadPerMillion() {

        return cacheReadPerMillion;
    }

    public void setCacheReadPerMillion(BigDecimal cacheReadPerMillion) {

        this.cacheReadPerMillion = cacheReadPerMillion;
    }

    public BigDecimal getCacheWritePerMillion() {

        return cacheWritePerMillion;
    }

    public void setCacheWritePerMillion(BigDecimal cacheWritePerMillion) {

        this.cacheWritePerMillion = cacheWritePerMillion;
    }
}
