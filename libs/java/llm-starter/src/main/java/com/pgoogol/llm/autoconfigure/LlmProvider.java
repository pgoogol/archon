package com.pgoogol.llm.autoconfigure;

/** Rodzina API providera — decyduje, którą implementację klienta postawić. */
public enum LlmProvider {

    /** {@code POST /v1/messages}. */
    ANTHROPIC("https://api.anthropic.com"),

    /**
     * {@code POST /v1/chat/completions} — pokrywa OpenAI, OpenRouter, Groq,
     * Mistral i serwery lokalne.
     */
    OPENAI("https://api.openai.com");

    private final String defaultBaseUrl;

    LlmProvider(String defaultBaseUrl) {

        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDefaultBaseUrl() {

        return defaultBaseUrl;
    }
}
