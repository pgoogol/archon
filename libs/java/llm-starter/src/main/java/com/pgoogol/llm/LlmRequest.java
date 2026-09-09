package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmUnsupportedInputException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Zapytanie do modelu: prompt systemowy, rozmowa i nadpisania konfiguracji.
 * Prompt systemowy jest opcjonalny.
 */
public record LlmRequest(String system, List<LlmMessage> messages, LlmOptions options) {

    public LlmRequest {

        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {

            throw new LlmUnsupportedInputException(LlmMessages.EMPTY_CONVERSATION);
        }
        messages = List.copyOf(messages);
        options = Objects.requireNonNullElse(options, LlmOptions.unset());
    }

    public static LlmRequest text(String system, String user) {

        LlmMessage message = LlmMessage.user(user);
        return new LlmRequest(system, List.of(message), LlmOptions.unset());
    }

    public static LlmRequest of(String system, ContentPart... parts) {

        LlmMessage message = LlmMessage.user(parts);
        return new LlmRequest(system, List.of(message), LlmOptions.unset());
    }

    public LlmRequest withOptions(LlmOptions value) {

        return new LlmRequest(system, messages, value);
    }

    public Optional<String> systemPrompt() {

        if (Objects.isNull(system) || system.isBlank()) {

            return Optional.empty();
        }
        return Optional.of(system);
    }

    /** Czy którakolwiek wiadomość niesie obraz. */
    public boolean hasImages() {

        return messages.stream().anyMatch(LlmMessage::hasImage);
    }
}
