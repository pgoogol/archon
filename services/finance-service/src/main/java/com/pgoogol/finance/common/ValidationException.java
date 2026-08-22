package com.pgoogol.finance.common;

/** Żądanie niespójne z regułami domeny — 400. */
public class ValidationException extends AppException {

    public ValidationException(String errorCode, String message) {

        super(errorCode, message);
    }
}
