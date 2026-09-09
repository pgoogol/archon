package com.pgoogol.llm;

/**
 * Powód zatrzymania generowania, sprowadzony do wspólnego mianownika obu
 * providerów. Bez tego wywołujący musiałby znać nazwy z API, a te się różnią:
 * Anthropic mówi {@code max_tokens}, OpenAI {@code length}.
 */
public enum StopReason {

    /** Model skończył wypowiedź sam. */
    COMPLETED,

    /** Wypowiedź urwał limit tokenów — treść jest niepełna. */
    MAX_TOKENS,

    /** Model odmówił odpowiedzi. */
    REFUSAL,

    /** Odpowiedź zatrzymał filtr treści providera. */
    FILTERED,

    /** Cokolwiek innego — najczęściej wywołanie narzędzia, którego tu nie używamy. */
    OTHER
}
