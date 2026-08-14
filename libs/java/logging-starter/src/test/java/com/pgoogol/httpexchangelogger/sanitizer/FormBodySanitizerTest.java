package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormBodySanitizerTest {

    private FormBodySanitizer sanitizerWithFields(List<String> fields) {

        HttpExchangeLoggerProperties.Mask mask = new HttpExchangeLoggerProperties.Mask();
        mask.setEnabled(true);
        mask.setFields(fields);
        SensitiveValueMasker masker = new SensitiveValueMasker(fields);
        return new FormBodySanitizer(masker, mask);
    }

    private FormBodySanitizer disabledSanitizer() {

        HttpExchangeLoggerProperties.Mask mask = new HttpExchangeLoggerProperties.Mask();
        mask.setEnabled(false);
        mask.setFields(List.of("password"));
        SensitiveValueMasker masker = new SensitiveValueMasker(mask.getFields());
        return new FormBodySanitizer(masker, mask);
    }

    @Test
    void sanitize_whenSensitivePairPresent_masksValue() {

        // given
        FormBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));

        // when
        String result = sanitizer.sanitize("user=alice&password=hunter2", "application/x-www-form-urlencoded");

        // then
        assertThat(result).isEqualTo("user=alice&password=***");
    }

    @Test
    void sanitize_whenSensitiveKeyUrlEncoded_decodesAndMasksValue() {

        // given
        FormBodySanitizer sanitizer = sanitizerWithFields(List.of("access token"));

        // when
        String result = sanitizer.sanitize("access%20token=abc&other=ok",
                "application/x-www-form-urlencoded");

        // then
        assertThat(result).contains("access%20token=***");
        assertThat(result).contains("other=ok");
        assertThat(result).doesNotContain("=abc");
    }

    @Test
    void sanitize_whenKeyCaseDiffers_masksCaseInsensitively() {

        // given
        FormBodySanitizer sanitizer = sanitizerWithFields(List.of("token"));

        // when
        String result = sanitizer.sanitize("Token=a&TOKEN=b&accessToken=c",
                "application/x-www-form-urlencoded");

        // then
        assertThat(result).contains("Token=***");
        assertThat(result).contains("TOKEN=***");
        assertThat(result).contains("accessToken=c");
    }

    @Test
    void sanitize_whenPairLacksEqualsSign_leavesItUnchanged() {

        // given
        FormBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));

        // when
        String result = sanitizer.sanitize("standalone&password=x",
                "application/x-www-form-urlencoded");

        // then
        assertThat(result).contains("standalone");
        assertThat(result).contains("password=***");
    }

    @Test
    void sanitize_whenMaskingDisabled_returnsOriginalBody() {

        // given
        FormBodySanitizer sanitizer = disabledSanitizer();
        String input = "password=secret";

        // when
        String result = sanitizer.sanitize(input, "application/x-www-form-urlencoded");

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void sanitize_whenContentTypeIsNotForm_returnsOriginalBody() {

        // given
        FormBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String input = "password=secret";

        // when
        String result = sanitizer.sanitize(input, "application/json");

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void sanitize_whenBodyIsNull_returnsNull() {

        // given
        FormBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));

        // when / then
        assertThat(sanitizer.sanitize(null, "application/x-www-form-urlencoded")).isNull();
    }

}
