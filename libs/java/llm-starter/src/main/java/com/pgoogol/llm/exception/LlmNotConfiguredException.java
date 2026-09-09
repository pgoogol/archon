package com.pgoogol.llm.exception;

/**
 * Brakuje czegoś, bez czego wywołanie nie ma sensu: klucza, modelu, klienta
 * o danej nazwie albo zasobu promptu. Serwis bez klucza wstaje normalnie i
 * działa „bez importu" — ten błąd pojawia się dopiero przy próbie użycia.
 */
public class LlmNotConfiguredException extends LlmException {

    public LlmNotConfiguredException(String message) {

        super(LlmErrorCodes.NOT_CONFIGURED, message);
    }

    public LlmNotConfiguredException(String message, Throwable cause) {

        super(LlmErrorCodes.NOT_CONFIGURED, message, cause);
    }
}
