package com.pgoogol.llm;

import java.util.Objects;

/** Fragment tekstowy wiadomości. */
public record TextPart(String text) implements ContentPart {

    public TextPart {

        Objects.requireNonNull(text, "text");
    }
}
