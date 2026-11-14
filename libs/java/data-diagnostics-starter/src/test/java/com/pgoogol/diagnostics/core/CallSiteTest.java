package com.pgoogol.diagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CallSiteTest {

    @Test
    @DisplayName("miejsce wywołania bez klasy nie wskazuje niczego, więc jest odrzucane")
    void constructor_whenClassNameBlank_fails() {

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CallSite(" ", "list", 42, null))
            .withMessageContaining("klasy");
    }

    @Test
    @DisplayName("miejsce wywołania bez metody jest odrzucane")
    void constructor_whenMethodBlank_fails() {

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CallSite("com.example.OrderService", "", 42, null))
            .withMessageContaining("metody");
    }
}
