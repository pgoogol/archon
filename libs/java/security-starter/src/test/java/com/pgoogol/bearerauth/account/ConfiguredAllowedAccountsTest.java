package com.pgoogol.bearerauth.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredAllowedAccountsTest {

    private static final List<String> ALLOWED = List.of("  Wlasciciel@Example.COM ", "", "drugi@example.com");

    @Test
    @DisplayName("isAllowed dla adresu z listy przepuszcza niezależnie od wielkości liter")
    void isAllowed_whenEmailOnList_returnsTrue() {

        // given
        ConfiguredAllowedAccounts accounts = new ConfiguredAllowedAccounts(ALLOWED);

        // when
        boolean allowed = accounts.isAllowed("WLASCICIEL@example.com");

        // then
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("isAllowed dla adresu spoza listy odmawia")
    void isAllowed_whenEmailNotOnList_returnsFalse() {

        // given
        ConfiguredAllowedAccounts accounts = new ConfiguredAllowedAccounts(ALLOWED);

        // when
        boolean allowed = accounts.isAllowed("obcy@example.com");

        // then
        assertThat(allowed).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("isAllowed dla pustego adresu odmawia")
    void isAllowed_whenEmailBlank_returnsFalse(String email) {

        // given
        ConfiguredAllowedAccounts accounts = new ConfiguredAllowedAccounts(ALLOWED);

        // when
        boolean allowed = accounts.isAllowed(email);

        // then
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("isAllowed dla adresu null odmawia")
    void isAllowed_whenEmailNull_returnsFalse() {

        // given
        ConfiguredAllowedAccounts accounts = new ConfiguredAllowedAccounts(ALLOWED);

        // when
        boolean allowed = accounts.isAllowed(null);

        // then
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("isAllowed przy liście null odmawia każdemu")
    void isAllowed_whenListNull_returnsFalse() {

        // given
        ConfiguredAllowedAccounts accounts = new ConfiguredAllowedAccounts(null);

        // when
        boolean allowed = accounts.isAllowed("wlasciciel@example.com");

        // then
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("isAllowed pomija puste wpisy i wartości null z konfiguracji")
    void isAllowed_whenListHasBlankEntries_ignoresThem() {

        // given
        List<String> withNulls = Arrays.asList(null, "  ", "jedyny@example.com");
        ConfiguredAllowedAccounts accounts = new ConfiguredAllowedAccounts(withNulls);

        // when / then
        assertThat(accounts.isAllowed("jedyny@example.com")).isTrue();
        assertThat(accounts.isAllowed("  ")).isFalse();
    }
}
