package com.pgoogol.httpexchangelogger.sanitizer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueMaskerTest {

    @Test
    void isSensitive_whenFieldNameMatchesIgnoringCase_returnsTrue() {

        // given
        SensitiveValueMasker masker = new SensitiveValueMasker(List.of("password", "Token"));

        // when / then
        assertThat(masker.isSensitive("password")).isTrue();
        assertThat(masker.isSensitive("PASSWORD")).isTrue();
        assertThat(masker.isSensitive("Token")).isTrue();
        assertThat(masker.isSensitive("token")).isTrue();
        assertThat(masker.isSensitive("productId")).isFalse();
    }

    @Test
    void mask_whenValueNonNull_returnsMaskAndPreservesNull() {

        // given
        SensitiveValueMasker masker = new SensitiveValueMasker(List.of("password"));

        // when / then
        assertThat(masker.mask("secret")).isEqualTo("***");
        assertThat(masker.mask(null)).isNull();
    }

    @Test
    void isSensitive_whenFieldsListIsNull_returnsFalse() {

        // given
        SensitiveValueMasker masker = new SensitiveValueMasker(null);

        // when / then
        assertThat(masker.isSensitive("password")).isFalse();
    }

}
