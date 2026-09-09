package com.pgoogol.kitchen.common;

import java.util.Objects;

/**
 * Wspólny przodek błędów domeny. Każdy niesie kod maszynowy — to on jest
 * kontraktem dla frontu, treść komunikatu jest wyłącznie dla człowieka.
 */
public abstract class AppException extends RuntimeException {

    private final String errorCode;

    protected AppException(String errorCode, String message) {

        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    protected AppException(String errorCode, String message, Throwable cause) {

        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public String getErrorCode() {

        return errorCode;
    }
}
