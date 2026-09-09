package com.pgoogol.llm.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Konfiguracja startera. Klienty są nazwane, bo jeden serwis potrafi używać
 * dwóch modeli naraz — tańszego do tekstu i takiego z wizją do zdjęć.
 *
 * <pre>
 * llm:
 *   default-client: text
 *   required-clients: [text, vision]
 *   clients:
 *     text:
 *       provider: anthropic
 *       api-key: ${KITCHEN_LLM_API_KEY:}
 *       model: ${KITCHEN_LLM_MODEL:}
 *     vision:
 *       provider: openai
 *       api-key: ${KITCHEN_LLM_VISION_API_KEY:}
 *       model: ${KITCHEN_LLM_VISION_MODEL:}
 *   pricing:
 *     "[claude-opus]":
 *       input-per-million: 15.00
 *       output-per-million: 75.00
 * </pre>
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private Map<String, LlmClientProperties> clients = new LinkedHashMap<>();

    /** Nazwa klienta zwracanego bez pytania. Przy jednym kliencie zbędna. */
    private String defaultClient;

    /**
     * Klienty, bez których serwis nie ma sensu. Sprawdzane przy starcie — brak
     * wpisu wychodzi wtedy, a nie przy pierwszym imporcie po wdrożeniu.
     */
    private List<String> requiredClients = new ArrayList<>();

    /**
     * Cennik po nazwie modelu albo jej przedrostku. Dopasowanie po najdłuższym
     * przedrostku, bo nazwy modeli niosą datę wydania i sam przedrostek jest
     * jedyną częścią, która nie zmienia się co kwartał.
     */
    private Map<String, LlmPricingProperties> pricing = new LinkedHashMap<>();

    public Map<String, LlmClientProperties> getClients() {

        return clients;
    }

    public void setClients(Map<String, LlmClientProperties> clients) {

        this.clients = clients;
    }

    public String getDefaultClient() {

        return defaultClient;
    }

    public void setDefaultClient(String defaultClient) {

        this.defaultClient = defaultClient;
    }

    public List<String> getRequiredClients() {

        return requiredClients;
    }

    public void setRequiredClients(List<String> requiredClients) {

        this.requiredClients = requiredClients;
    }

    public Map<String, LlmPricingProperties> getPricing() {

        return pricing;
    }

    public void setPricing(Map<String, LlmPricingProperties> pricing) {

        this.pricing = pricing;
    }
}
