package com.pgoogol.bearerauth.integration;

import com.pgoogol.bearerauth.autoconfigure.BearerAuthProperties;
import com.pgoogol.bearerauth.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.pgoogol.bearerauth.validation.GoogleTokenValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełny łańcuch filtrów na prawdziwym kontekście: obie drogi logowania, ścieżka
 * publiczna i kształt odpowiedzi błędu.
 */
@SpringBootTest(classes = BearerAuthIntegrationTest.TestApp.class)
@TestPropertySource(properties = {
        "bearer-auth.audience=" + TestTokens.AUDIENCE,
        "bearer-auth.allowed-emails=" + TestTokens.ALLOWED_EMAIL,
        "bearer-auth.public-paths=/actuator/health,/otwarte",
        "bearer-auth.local-account.username=admin"
})
class BearerAuthIntegrationTest {

    private static final TestTokens TOKENS = new TestTokens();

    private static final String PROTECTED_PATH = "/chronione";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        // łańcuch filtrów wpinamy jawnie: bez tego MockMvc omija Spring Security
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("żądanie bez poświadczeń dostaje 401 w formacie błędu API")
    void request_whenNoCredentials_returnsUnauthorized() throws Exception {

        // given / when / then
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("odpowiedź 401 nie niesie nagłówka WWW-Authenticate")
    void request_whenNoCredentials_doesNotAskBrowserForPassword() throws Exception {

        // given / when / then
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("ścieżka publiczna działa bez poświadczeń")
    void request_whenPathIsPublic_passesWithoutCredentials() throws Exception {

        // given / when / then
        mockMvc.perform(get("/otwarte"))
                .andExpect(status().isOk())
                .andExpect(content().string("otwarte"));
    }

    @Test
    @DisplayName("poprawny token konta z listy wpuszcza")
    void request_whenTokenValidAndEmailAllowed_passes() throws Exception {

        // given
        String token = TOKENS.valid();

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("token podpisany obcym kluczem dostaje 401")
    void request_whenTokenSignedByStranger_returnsUnauthorized() throws Exception {

        // given
        String token = TOKENS.signedByStranger();

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token po terminie ważności dostaje 401")
    void request_whenTokenExpired_returnsUnauthorized() throws Exception {

        // given
        String token = TOKENS.expired();

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token obcego wystawcy dostaje 401")
    void request_whenTokenFromForeignIssuer_returnsUnauthorized() throws Exception {

        // given
        String token = TOKENS.withIssuer("https://zly-wystawca.example.com");

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token wystawiony dla innej aplikacji dostaje 401")
    void request_whenTokenForAnotherAudience_returnsUnauthorized() throws Exception {

        // given
        String token = TOKENS.withAudience("obca-aplikacja.apps.googleusercontent.com");

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("poprawny token konta spoza listy dostaje 403, nie 401")
    void request_whenEmailNotAllowed_returnsForbidden() throws Exception {

        // given
        String token = TOKENS.withEmail("obcy@example.com", true);

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("token z niezweryfikowanym adresem dostaje 403 mimo obecności na liście")
    void request_whenEmailNotVerified_returnsForbidden() throws Exception {

        // given
        String token = TOKENS.withEmail(TestTokens.ALLOWED_EMAIL, false);

        // when / then
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("konto lokalne z poprawnym hasłem wpuszcza")
    void request_whenLocalAccountPasswordCorrect_passes() throws Exception {

        // given / when / then
        mockMvc.perform(get(PROTECTED_PATH).with(basic("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("konto lokalne ze złym hasłem dostaje 401")
    void request_whenLocalAccountPasswordWrong_returnsUnauthorized() throws Exception {

        // given / when / then
        mockMvc.perform(get(PROTECTED_PATH).with(basic("admin", "zle-haslo")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("nieznany login dostaje 401")
    void request_whenLocalAccountUnknown_returnsUnauthorized() throws Exception {

        // given / when / then
        mockMvc.perform(get(PROTECTED_PATH).with(basic("nikt", "admin")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("whoami dla konta lokalnego zwraca login i sposób logowania")
    void whoAmI_whenLocalAccount_returnsLocalSubject() throws Exception {

        // given / when / then
        mockMvc.perform(get("/auth/whoami").with(basic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("admin"))
                .andExpect(jsonPath("$.authentication").value("local"));
    }

    @Test
    @DisplayName("whoami dla konta Google zwraca adres e-mail")
    void whoAmI_whenGoogleAccount_returnsEmailSubject() throws Exception {

        // given
        String token = TOKENS.valid();

        // when / then
        mockMvc.perform(get("/auth/whoami").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(TestTokens.ALLOWED_EMAIL))
                .andExpect(jsonPath("$.authentication").value("google"));
    }

    @Test
    @DisplayName("odpowiedź niesie nagłówki bezpieczeństwa")
    void request_whenAnswered_carriesSecurityHeaders() throws Exception {

        // given / when
        String csp = mockMvc.perform(get("/otwarte"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andReturn()
                .getResponse()
                .getHeader("Content-Security-Policy");

        // then
        assertThat(csp).isEqualTo("default-src 'self'");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor basic(
            String username, String password) {

        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .httpBasic(username, password);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        /**
         * Dekoder na kluczu testowym zamiast na JWKS Google — walidatory
         * zostają te same, więc test przechodzi przez produkcyjny łańcuch
         * sprawdzeń, tylko bez sieci.
         */
        @Bean
        JwtDecoder jwtDecoder(BearerAuthProperties properties) {

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TOKENS.publicKey()).build();
            List<String> issuers = properties.getIssuers();
            decoder.setJwtValidator(new GoogleTokenValidator(issuers, properties.getAudience()));
            return decoder;
        }

        @RestController
        static class TestController {

            @GetMapping("/chronione")
            String protectedResource() {

                return "chronione";
            }

            @GetMapping("/otwarte")
            String publicResource() {

                return "otwarte";
            }
        }
    }
}
