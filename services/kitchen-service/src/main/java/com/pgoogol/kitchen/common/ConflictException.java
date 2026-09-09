package com.pgoogol.kitchen.common;

/** Stan zasobu nie pozwala na tę operację — 409. */
public class ConflictException extends AppException {

    public ConflictException(String errorCode, String message) {

        super(errorCode, message);
    }
}
