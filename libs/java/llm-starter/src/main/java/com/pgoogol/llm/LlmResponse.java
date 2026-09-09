package com.pgoogol.llm;

import java.util.Objects;

/**
 * Odpowiedź modelu sprowadzona do wspólnej postaci: tekst, powód zatrzymania,
 * zużycie i model, który faktycznie odpowiedział (provider potrafi podmienić
 * alias na konkretną wersję).
 */
public record LlmResponse(String text, StopReason stopReason, LlmUsage usage, String model) {

    public LlmResponse {

        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(stopReason, "stopReason");
        usage = Objects.requireNonNullElse(usage, LlmUsage.none());
        model = Objects.toString(model, "");
    }

    /** Czy treść jest kompletna, czy urwana limitem tokenów. */
    public boolean truncated() {

        return Objects.equals(stopReason, StopReason.MAX_TOKENS);
    }
}
