package com.pgoogol.finance.common;

/** Zewnętrzne API zawiodło — 502. Tylko ten typ jest ponawiany przez ApiCallGuard. */
public class ExternalServiceException extends AppException {

    public ExternalServiceException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ExternalServiceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
