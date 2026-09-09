package com.pgoogol.llm;

/**
 * Rola nadawcy wiadomości. Prompt systemowy nie jest tu rolą — siedzi osobno
 * w {@link LlmRequest#system()}, bo providery przyjmują go w innym miejscu
 * żądania niż resztę rozmowy.
 */
public enum Role {

    USER,
    ASSISTANT;

    public String wireValue() {

        return name().toLowerCase();
    }
}
