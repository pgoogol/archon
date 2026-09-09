package com.pgoogol.bearerauth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Odpowiedź na brak albo nieważność poświadczeń.
 *
 * <p>Świadomie bez nagłówka {@code WWW-Authenticate}: domyślny punkt wejścia
 * uwierzytelniania Basic go wysyła, a przeglądarka odpowiada na niego własnym
 * oknem logowania — nieostylowanym i omijającym bramkę we froncie.</p>
 */
public class BearerAuthEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = "Brak poświadczeń albo poświadczenia nieważne";

    private final AuthErrorWriter errorWriter;

    public BearerAuthEntryPoint(AuthErrorWriter errorWriter) {

        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), AuthErrorCodes.UNAUTHORIZED, MESSAGE);
    }
}
