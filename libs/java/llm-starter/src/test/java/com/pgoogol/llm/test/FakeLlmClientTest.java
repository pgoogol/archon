package com.pgoogol.llm.test;

import com.pgoogol.llm.JsonSchema;
import com.pgoogol.llm.LlmExtraction;
import com.pgoogol.llm.LlmRequest;
import com.pgoogol.llm.LlmResponse;
import com.pgoogol.llm.LlmUsage;
import com.pgoogol.llm.StopReason;
import com.pgoogol.llm.exception.LlmErrorCodes;
import com.pgoogol.llm.exception.LlmRateLimitedException;
import com.pgoogol.llm.exception.LlmResponseException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeLlmClientTest {

    private static final JsonSchema SCHEMA = new JsonSchema("dish", "{\"type\":\"object\"}");

    @Test
    void complete_whenAnswersScripted_returnsThemInOrderAndRecordsRequests() {

        // given
        FakeLlmClient client = new FakeLlmClient();
        client.answerWith("pierwsza").answerWith("druga");

        // when
        String first = client.complete(LlmRequest.text("system", "raz")).text();
        String second = client.complete(LlmRequest.text("system", "dwa")).text();

        // then
        assertThat(first).isEqualTo("pierwsza");
        assertThat(second).isEqualTo("druga");
        assertThat(client.requests()).hasSize(2);
        assertThat(client.lastRequest()).get().extracting(LlmRequest::system).isEqualTo("system");
    }

    @Test
    void complete_whenFailureScripted_throwsIt() {

        // given
        FakeLlmClient client = new FakeLlmClient();
        client.failWith(new LlmRateLimitedException("limit", Duration.ofSeconds(1)));
        LlmRequest request = LlmRequest.text("system", "raz");

        // when, then
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmRateLimitedException.class);
    }

    @Test
    void extract_whenAnswerIsJson_mapsItToTheRecord() {

        // given
        FakeLlmClient client = new FakeLlmClient();
        client.answerWith("{\"title\":\"Sernik\",\"servings\":12}");

        // when
        LlmExtraction<Dish> extraction = client.extract(LlmRequest.text("s", "u"), SCHEMA, Dish.class);

        // then
        assertThat(extraction.value()).isEqualTo(new Dish("Sernik", 12));
        assertThat(extraction.rawJson()).contains("Sernik");
    }

    @Test
    void extract_whenScriptedAnswerIsTruncated_behavesLikeTheRealClient() {

        // given
        FakeLlmClient client = new FakeLlmClient();
        client.answerWith(new LlmResponse("{\"title\":\"Ser", StopReason.MAX_TOKENS, LlmUsage.none(), "fake"));
        LlmRequest request = LlmRequest.text("s", "u");

        // when, then
        assertThatThrownBy(() -> client.extract(request, SCHEMA, Dish.class))
                .isInstanceOf(LlmResponseException.class)
                .hasFieldOrPropertyWithValue("errorCode", LlmErrorCodes.RESPONSE_TRUNCATED);
    }

    @Test
    void reset_whenCalled_clearsScriptAndRequests() {

        // given
        FakeLlmClient client = new FakeLlmClient();
        client.answerWith("pierwsza");
        client.complete(LlmRequest.text("system", "raz"));

        // when
        client.reset();

        // then
        assertThat(client.requests()).isEmpty();
        assertThat(client.lastRequest()).isEmpty();
    }

    record Dish(String title, int servings) {

    }
}
