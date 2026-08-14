package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHeaderSanitizerTest {

    private DefaultHeaderSanitizer sanitizerWithFields(List<String> fields, boolean enabled) {

        HttpExchangeLoggerProperties.Mask mask = new HttpExchangeLoggerProperties.Mask();
        mask.setEnabled(enabled);
        mask.setFields(fields);
        return new DefaultHeaderSanitizer(new SensitiveValueMasker(fields), mask);
    }

    @Test
    void sanitize_whenHeaderIsBuiltInSensitive_masksEvenWhenNotInConfiguredList() {

        // given
        DefaultHeaderSanitizer sanitizer = sanitizerWithFields(List.of(), true);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("Cookie", List.of("session=xyz"));
        headers.put("X-Api-Key", List.of("key1"));
        headers.put("Content-Type", List.of("application/json"));

        // when
        Map<String, List<String>> result = sanitizer.sanitize(headers);

        // then
        assertThat(result.get("Authorization")).containsExactly("***");
        assertThat(result.get("Cookie")).containsExactly("***");
        assertThat(result.get("X-Api-Key")).containsExactly("***");
        assertThat(result.get("Content-Type")).containsExactly("application/json");
    }

    @Test
    void sanitize_whenHeaderNamesDifferInCase_masksCaseInsensitively() {

        // given
        DefaultHeaderSanitizer sanitizer = sanitizerWithFields(List.of("x-trace"), true);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("AUTHORIZATION", List.of("Bearer secret"));
        headers.put("X-Trace", List.of("trace-id"));

        // when
        Map<String, List<String>> result = sanitizer.sanitize(headers);

        // then
        assertThat(result.get("AUTHORIZATION")).containsExactly("***");
        assertThat(result.get("X-Trace")).containsExactly("***");
    }

    @Test
    void sanitize_whenHeaderHasMultipleValues_masksAllValues() {

        // given
        DefaultHeaderSanitizer sanitizer = sanitizerWithFields(List.of(), true);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Set-Cookie", List.of("a=1", "b=2"));

        // when
        Map<String, List<String>> result = sanitizer.sanitize(headers);

        // then
        assertThat(result.get("Set-Cookie")).containsExactly("***", "***");
    }

    @Test
    void sanitize_whenMaskingDisabled_stillMasksBuiltInCredentialHeadersButNotConfiguredOnes() {

        // given
        DefaultHeaderSanitizer sanitizer = sanitizerWithFields(List.of("x-custom"), false);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("X-Custom", List.of("value"));

        // when
        Map<String, List<String>> result = sanitizer.sanitize(headers);

        // then
        // Built-in credential headers are always masked, even with masking disabled...
        assertThat(result.get("Authorization")).containsExactly("***");
        // ...but extra configured header names are only masked when masking is enabled.
        assertThat(result.get("X-Custom")).containsExactly("value");
    }

}
