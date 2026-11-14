package com.pgoogol.diagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DataStoreTest {

    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "sql-server", "db2"})
    @DisplayName("nazwa z małych liter, cyfr i myślników nadaje się na klucz ustawień")
    void constructor_whenNameIsLowercaseKebab_keepsName(String name) {

        // when
        DataStore store = new DataStore(name);

        // then
        assertThat(store.name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "PostgreSQL", "my_db", "-pg", "pg-", "pg sql"})
    @DisplayName("nazwa, która rozjechałaby klucze ustawień i tagi metryk, jest odrzucana")
    void constructor_whenNameIsNotLowercaseKebab_fails(String name) {

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new DataStore(name))
            .withMessageContaining("małe litery");
    }
}
