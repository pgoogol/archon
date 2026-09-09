package com.pgoogol.llm.exception;

/** Provider odesłał 5xx albo nie odpowiedział w czasie. Ponawialne. */
public class LlmUnavailableException extends LlmException {

    public LlmUnavailableException(String message, Throwable cause) {

        super(LlmErrorCodes.UNAVAILABLE, message, cause);
    }
}
