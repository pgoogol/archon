package com.pgoogol.music.common;

/**
 * Zasób istnieje, ale użyty token nie ma do niego dostępu → HTTP 403.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String errorCode, String message) {

        super(errorCode, message);
    }
}
