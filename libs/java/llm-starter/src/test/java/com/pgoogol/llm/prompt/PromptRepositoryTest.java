package com.pgoogol.llm.prompt;

import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.exception.LlmNotConfiguredException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRepositoryTest {

    private final PromptRepository repository = new PromptRepository();

    @Test
    void load_whenPromptExists_readsSystemUserAndSchema() {

        // when
        Prompt prompt = repository.load("test-extraction", "v1");

        // then
        assertThat(prompt.system()).startsWith("Jesteś parserem przepisów");
        assertThat(prompt.userTemplate()).contains("{{source}}");
        JsonSchema schema = prompt.requiredSchema();
        assertThat(schema.name()).isEqualTo("test-extraction");
        assertThat(schema.json()).contains("\"ingredients\"");
    }

    @Test
    void load_whenCalledTwice_returnsTheSameInstance() {

        // when
        Prompt first = repository.load("test-extraction", "v1");
        Prompt second = repository.load("test-extraction", "v1");

        // then
        assertThat(first).isSameAs(second);
    }

    @Test
    void load_whenPromptMissing_reportsMissingConfiguration() {

        // when, then
        assertThatThrownBy(() -> repository.load("nie-ma-takiego", "v1"))
                .isInstanceOf(LlmNotConfiguredException.class)
                .hasMessageContaining("llm/nie-ma-takiego/v1/system.md");
    }

    @Test
    void user_whenVariablesGiven_fillsThePlaceholders() {

        // given
        Prompt prompt = repository.load("test-extraction", "v1");

        // when
        String filled = prompt.user(Map.of("source", "https://example.test/x", "language", "polski"));

        // then
        assertThat(filled).contains("Źródło: https://example.test/x").contains("Język wyjścia: polski");
    }

    @Test
    void user_whenVariableMissing_failsInsteadOfSendingPromptWithHole() {

        // given
        Prompt prompt = repository.load("test-extraction", "v1");
        Map<String, String> variables = Map.of("source", "https://example.test/x");

        // when, then
        assertThatThrownBy(() -> prompt.user(variables))
                .isInstanceOf(LlmNotConfiguredException.class)
                .hasMessageContaining("language");
    }

    @Test
    void user_whenValueCarriesDollarSign_insertsItLiterally() {

        // given: wartość z użytkownika trafia do replaceAll, gdzie $1 znaczy grupę
        Prompt prompt = repository.load("test-extraction", "v1");

        // when
        String filled = prompt.user(Map.of("source", "cena $1 za sztukę", "language", "polski"));

        // then
        assertThat(filled).contains("cena $1 za sztukę");
    }

    @Test
    void requiredSchema_whenPromptHasNoSchemaFile_reportsIt() {

        // given
        PromptRepository other = new PromptRepository("llm-bez-schematu");

        // when
        Prompt prompt = other.load("proste", "v1");

        // then
        assertThat(prompt.schema()).isEmpty();
        assertThatThrownBy(prompt::requiredSchema)
                .isInstanceOf(LlmNotConfiguredException.class)
                .hasMessageContaining("schema.json");
    }
}
