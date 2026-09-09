package com.pgoogol.llm;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.llm.autoconfigure.LlmClientProperties;
import com.pgoogol.llm.autoconfigure.LlmProvider;
import com.pgoogol.llm.prompt.Prompt;
import com.pgoogol.llm.prompt.PromptRepository;
import com.pgoogol.llm.support.LlmFixtures;
import com.pgoogol.llm.support.LlmWireMockStubs;
import com.pgoogol.llm.usage.LlmUsageEvent;
import com.pgoogol.llm.usage.LlmUsageListener;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.pgoogol.llm.support.LlmWireMockStubs.ANTHROPIC_PATH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cała droga startera na jednym przykładzie: prompt z zasobu, tekst i zdjęcie
 * w jednym zapytaniu, schemat pilnujący kształtu, rekord na wyjściu i zużycie
 * u słuchacza. To, co serwis kuchenny będzie robił przy imporcie z grafiki.
 */
@WireMockTest
class LlmExtractionFlowTest {

    private static final byte[] PHOTO = "udawane-zdjecie-przepisu".getBytes(StandardCharsets.UTF_8);

    @Test
    void extract_whenPromptCarriesTextAndImage_returnsRecordAndReportsUsage(WireMockRuntimeInfo wireMock) {

        // given
        LlmWireMockStubs.anthropicAnswers("""
                {"title": "Sernik wiedeński", "ingredients": [
                  {"name": "twaróg", "quantity": "1 kg"},
                  {"name": "cukier", "quantity": "200 g"}
                ]}""");
        List<LlmUsageEvent> usage = new ArrayList<>();
        LlmClient client = client(wireMock, usage::add);
        Prompt prompt = new PromptRepository().load("test-extraction", "v1");
        String user = prompt.user(Map.of("source", "zdjęcie z telefonu", "language", "polski"));
        LlmRequest request = LlmRequest.of(prompt.system(), new TextPart(user),
                new ImagePart(PHOTO, "image/jpeg"));

        // when
        LlmExtraction<Recipe> extraction = client.extract(request, prompt.requiredSchema(), Recipe.class);

        // then
        assertThat(extraction.value().title()).isEqualTo("Sernik wiedeński");
        assertThat(extraction.value().ingredients())
                .extracting(Ingredient::name)
                .containsExactly("twaróg", "cukier");
        assertThat(extraction.usage().totalTokens()).isEqualTo(165);
        assertThat(usage).singleElement().satisfies(event ->
                assertThat(event.clientName()).isEqualTo("vision"));

        // i to, co faktycznie poszło do modelu
        JsonNode body = LlmWireMockStubs.lastRequestBody(ANTHROPIC_PATH);
        assertThat(body.path("system").asString()).startsWith("Jesteś parserem przepisów");
        assertThat(body.path("messages").path(0).path("content").path(0).path("text").asString())
                .contains("Źródło: zdjęcie z telefonu");
        assertThat(body.path("messages").path(0).path("content").path(1).path("type").asString())
                .isEqualTo("image");
        assertThat(body.path("output_config").path("format").path("schema").path("required"))
                .hasToString("[\"title\",\"ingredients\"]");
    }

    private LlmClient client(WireMockRuntimeInfo wireMock, LlmUsageListener listener) {

        LlmClientProperties config = LlmFixtures.config(LlmProvider.ANTHROPIC, wireMock.getHttpBaseUrl());
        return LlmFixtures.client("vision", config, LlmFixtures.publisher(List.of(listener)));
    }

    record Recipe(String title, List<Ingredient> ingredients) {

    }

    record Ingredient(String name, String quantity) {

    }
}
