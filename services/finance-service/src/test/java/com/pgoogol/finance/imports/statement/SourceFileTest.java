package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileTest {

    @Test
    @DisplayName("fileHash dla tej samej zawartości daje ten sam skrót")
    void fileHash_forSameContent_returnsSameDigest() {

        // given
        SourceFile first = new SourceFile("a.csv", "treść".getBytes(StandardCharsets.UTF_8));
        SourceFile second = new SourceFile("inna-nazwa.csv",
            "treść".getBytes(StandardCharsets.UTF_8));

        // when & then: nazwa pliku nie wchodzi do skrótu — ten sam eksport zapisany
        // pod inną nazwą to wciąż ten sam wyciąg
        assertThat(first.fileHash()).isEqualTo(second.fileHash());
    }

    @Test
    @DisplayName("fileHash dla różnej zawartości daje różne skróty")
    void fileHash_forDifferentContent_returnsDifferentDigests() {

        // given
        SourceFile first = new SourceFile("a.csv", "treść".getBytes(StandardCharsets.UTF_8));
        SourceFile second = new SourceFile("a.csv", "inna treść".getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThat(first.fileHash()).isNotEqualTo(second.fileHash());
    }

    @Test
    @DisplayName("fileHash ma długość skrótu SHA-256 zapisanego szesnastkowo")
    void fileHash_hasSha256HexLength() {

        // given
        SourceFile file = new SourceFile("a.csv", "x".getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThat(file.fileHash()).hasSize(64);
    }

    @Test
    @DisplayName("konstruktor gdy plik jest pusty, wywala się")
    void constructor_whenFileIsEmpty_throws() {

        // when & then
        assertThatThrownBy(() -> new SourceFile("a.csv", new byte[0]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pusty");
    }

    @Test
    @DisplayName("text czyta zawartość we wskazanym kodowaniu")
    void text_readsContentInGivenCharset() {

        // given: polskie znaki zapisane w windows-1250
        byte[] content = "zażółć".getBytes(Charset.forName("windows-1250"));
        SourceFile file = new SourceFile("a.csv", content);

        // when
        String text = file.text(Charset.forName("windows-1250"));

        // then
        assertThat(text).isEqualTo("zażółć");
    }
}
