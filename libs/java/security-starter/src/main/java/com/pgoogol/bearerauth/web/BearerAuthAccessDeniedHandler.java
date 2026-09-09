package com.pgoogol.bearerauth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Odpowiedź dla podmiotu rozpoznanego, ale bez wstępu — konto spoza listy
 * dozwolonych albo adres e-mail niezweryfikowany po stronie Google.
 */
public class BearerAuthAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "Konto nie ma dostępu do tej aplikacji";

    private final AuthErrorWriter errorWriter;

    public BearerAuthAccessDeniedHandler(AuthErrorWriter errorWriter) {

        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        errorWriter.write(response, HttpStatus.FORBIDDEN.value(), AuthErrorCodes.FORBIDDEN, MESSAGE);
    }
}
