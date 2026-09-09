package com.pgoogol.llm.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

    @Test
    void mask_whenTextCarriesTheApiKey_replacesIt() {

        // given
        SecretMasker masker = new SecretMasker("sk-tajny-klucz-1234");

        // when
        String masked = masker.mask("nieznany klucz sk-tajny-klucz-1234 w żądaniu");

        // then
        assertThat(masked).isEqualTo("nieznany klucz *** w żądaniu").doesNotContain("sk-tajny");
    }

    @Test
    void mask_whenSecretIsShort_leavesTextAlone() {

        // given: krótki „sekret" trafiłby w przypadkowe fragmenty i zamazał treść błędu
        SecretMasker masker = new SecretMasker("abc");

        // when
        String masked = masker.mask("abcdefgh nie jest kluczem");

        // then
        assertThat(masked).isEqualTo("abcdefgh nie jest kluczem");
    }

    @Test
    void mask_whenKeyIsNotConfigured_returnsTextUnchanged() {

        // given
        SecretMasker masker = new SecretMasker(new String[]{null});

        // when
        String masked = masker.mask("treść błędu");

        // then
        assertThat(masked).isEqualTo("treść błędu");
    }

    @Test
    void mask_whenTextIsMissing_returnsEmptyString() {

        // given
        SecretMasker masker = new SecretMasker("sk-tajny-klucz-1234");

        // when
        String masked = masker.mask(null);

        // then
        assertThat(masked).isEmpty();
    }
}
