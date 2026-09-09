package com.pgoogol.llm.autoconfigure;

import com.pgoogol.llm.LlmClient;
import com.pgoogol.llm.LlmClients;
import com.pgoogol.llm.exception.LlmNotConfiguredException;
import com.pgoogol.llm.prompt.PromptRepository;
import com.pgoogol.llm.provider.AnthropicLlmClient;
import com.pgoogol.llm.provider.OpenAiCompatibleLlmClient;
import com.pgoogol.llm.usage.CostEstimator;
import com.pgoogol.llm.usage.LlmUsageListener;
import com.pgoogol.llm.usage.LlmUsagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LlmAutoConfiguration.class));

    @Test
    void context_whenTwoClientsConfigured_registersBothWithTheirProviders() {

        runner.withPropertyValues(
                        "llm.clients.text.provider=anthropic",
                        "llm.clients.text.model=model-tekstowy",
                        "llm.clients.vision.provider=openai",
                        "llm.clients.vision.model=model-wizyjny")
                .run(context -> {

                    LlmClients clients = context.getBean(LlmClients.class);
                    assertThat(clients.names()).containsExactlyInAnyOrder("text", "vision");
                    assertThat(clients.client("text")).isInstanceOf(AnthropicLlmClient.class);
                    assertThat(clients.client("vision")).isInstanceOf(OpenAiCompatibleLlmClient.class);
                });
    }

    @Test
    void context_whenRequiredClientMissing_failsAtStartup() {

        runner.withPropertyValues(
                        "llm.clients.text.model=model-tekstowy",
                        "llm.required-clients=text,vision")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(LlmNotConfiguredException.class)
                        .hasMessageContaining("vision"));
    }

    @Test
    void context_whenNoApiKey_stillStartsSoTheServiceWorksWithoutImport() {

        runner.withPropertyValues("llm.clients.text.model=model-tekstowy")
                .run(context -> {

                    assertThat(context).hasNotFailed();
                    LlmClient client = context.getBean(LlmClients.class).defaultClient();
                    assertThat(client.name()).isEqualTo("text");
                });
    }

    @Test
    void defaultClient_whenSeveralConfiguredAndNoneChosen_saysWhatToSet() {

        runner.withPropertyValues(
                        "llm.clients.text.model=model-tekstowy",
                        "llm.clients.vision.model=model-wizyjny")
                .run(context -> {

                    LlmClients clients = context.getBean(LlmClients.class);
                    assertThatThrownBy(clients::defaultClient)
                            .isInstanceOf(LlmNotConfiguredException.class)
                            .hasMessageContaining("llm.default-client");
                });
    }

    @Test
    void defaultClient_whenChosenInConfiguration_returnsThatOne() {

        runner.withPropertyValues(
                        "llm.default-client=vision",
                        "llm.clients.text.model=model-tekstowy",
                        "llm.clients.vision.model=model-wizyjny")
                .run(context -> {

                    LlmClient client = context.getBean(LlmClients.class).defaultClient();
                    assertThat(client.name()).isEqualTo("vision");
                });
    }

    @Test
    void context_whenNothingConfigured_registersEmptyRegistryInsteadOfFailing() {

        runner.run(context -> {

            assertThat(context).hasSingleBean(LlmClients.class).hasSingleBean(PromptRepository.class);
            assertThat(context.getBean(LlmClients.class).names()).isEmpty();
        });
    }

    @Test
    void context_whenServiceDefinesUsageListener_wiresItIntoThePublisher() {

        runner.withUserConfiguration(ListenerConfig.class)
                .run(context -> assertThat(context).hasSingleBean(LlmUsagePublisher.class)
                        .hasSingleBean(LlmUsageListener.class));
    }

    @Test
    void context_whenServiceDefinesItsOwnPromptRepository_startersBacksOff() {

        runner.withUserConfiguration(PromptConfig.class)
                .run(context -> assertThat(context.getBean(PromptRepository.class))
                        .isSameAs(context.getBean(PromptConfig.class).promptRepository()));
    }

    @Test
    void context_whenPricingConfigured_bindsItIntoTheEstimator() {

        runner.withPropertyValues("llm.pricing.[model-tekstowy].input-per-million=12.50")
                .run(context -> {

                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getPricing()).containsKey("model-tekstowy");
                    assertThat(context).hasSingleBean(CostEstimator.class);
                });
    }

    @Configuration
    static class ListenerConfig {

        private final List<Object> events = new ArrayList<>();

        @Bean
        LlmUsageListener recordingListener() {

            return events::add;
        }
    }

    @Configuration
    static class PromptConfig {

        private final PromptRepository repository = new PromptRepository("wlasne-prompty");

        @Bean
        PromptRepository promptRepository() {

            return repository;
        }
    }
}
