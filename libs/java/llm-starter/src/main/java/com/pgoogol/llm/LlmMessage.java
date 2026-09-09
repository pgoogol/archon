package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmUnsupportedInputException;

import java.util.List;
import java.util.Objects;

/** Jedna wiadomość rozmowy — rola i jej fragmenty treści. */
public record LlmMessage(Role role, List<ContentPart> parts) {

    public LlmMessage {

        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) {

            throw new LlmUnsupportedInputException(LlmMessages.EMPTY_MESSAGE);
        }
        parts = List.copyOf(parts);
    }

    public static LlmMessage user(String text) {

        TextPart part = new TextPart(text);
        return new LlmMessage(Role.USER, List.of(part));
    }

    public static LlmMessage user(ContentPart... parts) {

        List<ContentPart> content = List.of(parts);
        return new LlmMessage(Role.USER, content);
    }

    public static LlmMessage assistant(String text) {

        TextPart part = new TextPart(text);
        return new LlmMessage(Role.ASSISTANT, List.of(part));
    }

    /** Czy wiadomość niesie obraz — provider bez wizji ma odmówić od razu. */
    public boolean hasImage() {

        return parts.stream().anyMatch(ImagePart.class::isInstance);
    }
}
