package com.pgoogol.kitchen.common;

/** Żądanie jest niepoprawne — 400. */
public class ValidationException extends AppException {

    public ValidationException(String errorCode, String message) {

        super(errorCode, message);
    }
}
