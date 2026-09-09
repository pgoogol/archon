package com.pgoogol.llm.exception;

/**
 * Wspólny nadtyp błędów startera. Każdy niesie kod odczytywalny maszynowo —
 * serwis przekłada go na własny kod i status HTTP, bo starter nie wie, jak
 * wygląda jego API.
 */
public abstract class LlmException extends RuntimeException {

    private final String errorCode;

    protected LlmException(String errorCode, String message) {

        super(message);
        this.errorCode = errorCode;
    }

    protected LlmException(String errorCode, String message, Throwable cause) {

        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {

        return errorCode;
    }
}
