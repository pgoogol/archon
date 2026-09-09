package com.pgoogol.llm.exception;

/**
 * Wejście nie nadaje się do wysłania: pusty obraz, nieobsługiwany format,
 * zapytanie bez wiadomości. Sprawdzane u nas, zanim żądanie ruszy w sieć.
 */
public class LlmUnsupportedInputException extends LlmException {

    public LlmUnsupportedInputException(String message) {

        super(LlmErrorCodes.UNSUPPORTED_INPUT, message);
    }
}
