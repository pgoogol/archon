package com.pgoogol.llm.exception;

/**
 * Provider odesłał 4xx inne niż 429 — zły klucz, nieznany model, żądanie ponad
 * limit kontekstu. Ponawianie nic tu nie zmieni.
 */
public class LlmRequestRejectedException extends LlmException {

    private final int status;

    public LlmRequestRejectedException(String message, int status, Throwable cause) {

        super(LlmErrorCodes.REQUEST_REJECTED, message, cause);
        this.status = status;
    }

    public int getStatus() {

        return status;
    }
}
