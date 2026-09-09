package com.pgoogol.bearerauth.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Potwierdza, że serwis uznał poświadczenia.
 *
 * <p>Front woła ten adres zaraz po zalogowaniu, zanim wpuści do aplikacji.
 * Dla konta lokalnego to jedyny sposób sprawdzenia loginu z hasłem, bo
 * uwierzytelnianie Basic nie ma własnego endpointu logowania.</p>
 *
 * <p>Ścieżka stoi poza prefiksem domenowym serwisu, bo nie należy do żadnej
 * domeny — tak samo jak {@code /actuator} i {@code /v3/api-docs}. Powłoka
 * frontu może ją zawołać, nie znając nazwy żadnej domeny.</p>
 */
@RestController
@RequestMapping("/auth")
public class WhoAmIController {

    private static final String GOOGLE = "google";

    private static final String LOCAL = "local";

    @GetMapping("/whoami")
    public WhoAmIResponse whoAmI(Authentication authentication) {

        String kind = authenticationKind(authentication);
        return new WhoAmIResponse(authentication.getName(), kind);
    }

    private String authenticationKind(Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken) {

            return GOOGLE;
        }
        return LOCAL;
    }
}
