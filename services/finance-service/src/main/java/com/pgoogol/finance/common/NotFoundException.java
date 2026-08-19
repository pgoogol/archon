package com.pgoogol.finance.common;

/** Zasób nie istnieje — 404. */
public class NotFoundException extends AppException {

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
