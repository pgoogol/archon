package com.pgoogol.bearerauth.web;

/**
 * Tożsamość zalogowanego podmiotu.
 *
 * @param subject        adres e-mail konta Google albo login konta lokalnego
 * @param authentication sposób zalogowania: {@code google} albo {@code local}
 */
public record WhoAmIResponse(String subject, String authentication) {
}
