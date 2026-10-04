package com.pgoogol.testdiagnostics.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PropertyMaskerTest {

    private final PropertyMasker masker = new PropertyMasker();

    @Test
    @DisplayName("zwykła właściwość zostaje bez zmian")
    void mask_whenPlainProperty_keepsIt() {

        // when
        String masked = masker.mask("app.mode=fast");

        // then
        assertThat(masked).isEqualTo("app.mode=fast");
    }

    @Test
    @DisplayName("hasło, sekret, token i klucz: wartość znika, a różne wartości mają różny odcisk")
    void mask_whenSecretKey_hidesValueButKeepsThemDistinct() {

        // when
        String password = masker.mask("spring.datasource.password=s3cret");
        String otherPassword = masker.mask("spring.datasource.password=other");
        String colonToken = masker.mask("api.token: abc");
        String apiKey = masker.mask("stripe.API_KEY=sk_test");

        // then
        assertAll(
            () -> assertThat(password).startsWith("spring.datasource.password=*** #").doesNotContain("s3cret"),
            () -> assertThat(otherPassword).isNotEqualTo(password),
            () -> assertThat(colonToken).startsWith("api.token=*** #").doesNotContain("abc"),
            () -> assertThat(apiKey).startsWith("stripe.API_KEY=*** #").doesNotContain("sk_test"));
    }

    @Test
    @DisplayName("długa właściwość zostaje cała, bo przycina ją dopiero raport")
    void mask_whenLong_keepsIt() {

        // given
        String property = "app.allowed-origins=" + "x".repeat(100);

        // when
        String masked = masker.mask(property);

        // then
        assertThat(masked).isEqualTo(property);
    }
}
