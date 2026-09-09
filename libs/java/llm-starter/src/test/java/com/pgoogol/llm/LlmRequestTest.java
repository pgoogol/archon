package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmUnsupportedInputException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRequestTest {

    private static final byte[] IMAGE = "udawany-jpeg".getBytes(StandardCharsets.UTF_8);

    @Test
    void constructor_whenConversationEmpty_refusesTheRequest() {

        // given
        List<LlmMessage> messages = List.of();

        // when, then
        assertThatThrownBy(() -> new LlmRequest("system", messages, null))
                .isInstanceOf(LlmUnsupportedInputException.class);
    }

    @Test
    void constructor_whenOptionsMissing_fallsBackToUnset() {

        // when
        LlmRequest request = new LlmRequest("system", List.of(LlmMessage.user("cześć")), null);

        // then
        assertThat(request.options()).isEqualTo(LlmOptions.unset());
        assertThat(request.options().temperatureValue()).isEmpty();
    }

    @Test
    void systemPrompt_whenBlank_isTreatedAsAbsent() {

        // when
        LlmRequest request = LlmRequest.text("   ", "cześć");

        // then
        assertThat(request.systemPrompt()).isEmpty();
    }

    @Test
    void hasImages_whenAnyPartIsAnImage_saysSo() {

        // when
        LlmRequest request = LlmRequest.of("system", new TextPart("co to?"), new ImagePart(IMAGE, "image/jpeg"));

        // then
        assertThat(request.hasImages()).isTrue();
    }

    @Test
    void constructor_whenImageFormatUnsupported_failsBeforeAnythingLeavesTheProcess() {

        // when, then
        assertThatThrownBy(() -> new ImagePart(IMAGE, "image/tiff"))
                .isInstanceOf(LlmUnsupportedInputException.class)
                .hasMessageContaining("image/tiff");
    }

    @Test
    void constructor_whenImageEmpty_refusesIt() {

        // given
        byte[] empty = new byte[0];

        // when, then
        assertThatThrownBy(() -> new ImagePart(empty, "image/png"))
                .isInstanceOf(LlmUnsupportedInputException.class);
    }

    @Test
    void bytes_whenSourceArrayChangesLater_partKeepsItsOwnCopy() {

        // given
        byte[] source = IMAGE.clone();
        ImagePart part = new ImagePart(source, "image/png");

        // when
        source[0] = 0;

        // then
        assertThat(part.bytes()).isEqualTo(IMAGE);
    }

    @Test
    void constructor_whenTemperatureOutOfRange_refusesTheOptions() {

        // when, then
        assertThatThrownBy(() -> new LlmOptions(null, null, 3.0))
                .isInstanceOf(LlmUnsupportedInputException.class);
    }

    @Test
    void constructor_whenMaxTokensNotPositive_refusesTheOptions() {

        // when, then
        assertThatThrownBy(() -> new LlmOptions(0, null, null))
                .isInstanceOf(LlmUnsupportedInputException.class);
    }
}
