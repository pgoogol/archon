package com.pgoogol.llm.autoconfigure;

import com.pgoogol.llm.LlmClients;
import com.pgoogol.llm.prompt.PromptRepository;
import com.pgoogol.llm.provider.LlmClientFactory;
import com.pgoogol.llm.usage.CostEstimator;
import com.pgoogol.llm.usage.LlmUsageListener;
import com.pgoogol.llm.usage.LlmUsagePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Włącza się przez samo dodanie zależności. Serwis nic nie importuje ręcznie —
 * wystarczy, że skonfiguruje {@code llm.clients.*}.
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmProperties.class)
@ConditionalOnClass({RestClient.class, ObjectMapper.class})
public class LlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PromptRepository promptRepository() {

        return new PromptRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public CostEstimator llmCostEstimator(LlmProperties properties) {

        return new CostEstimator(properties.getPricing());
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmUsagePublisher llmUsagePublisher(ObjectProvider<LlmUsageListener> listeners,
                                               CostEstimator costEstimator) {

        List<LlmUsageListener> ordered = listeners.orderedStream().toList();
        return new LlmUsagePublisher(ordered, costEstimator);
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmClients llmClients(LlmProperties properties,
                                 ObjectProvider<RestClient.Builder> restClientBuilders,
                                 ObjectProvider<ObjectMapper> objectMappers,
                                 LlmUsagePublisher usagePublisher) {

        RestClient.Builder builder = restClientBuilders.getIfAvailable(RestClient::builder);
        ObjectMapper objectMapper = objectMappers.getIfAvailable(() -> JsonMapper.builder().build());
        LlmClientFactory factory = new LlmClientFactory(builder, objectMapper, usagePublisher);
        return factory.create(properties);
    }
}
