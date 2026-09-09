package com.pgoogol.finance.api;

import com.pgoogol.finance.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pozostałe testy integracyjne chodzą w profilu bez uwierzytelniania, więc nie
 * powiedzą nic o tym, czy łańcuch filtrów jest w ogóle wpięty. Ten jeden
 * świadomie wraca do profilu domyślnego i sprawdza właśnie to — samą logikę
 * uwierzytelniania pokrywają testy security-startera.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("default")
@Tag("integration")
class SecurityChainSmokeTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    @DisplayName("żądanie do API bez poświadczeń dostaje 401 w formacie błędu serwisu")
    void request_whenNoCredentials_returnsUnauthorized() throws Exception {

        // given / when / then
        mockMvc.perform(get("/finance/api/v1/nieistotne"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("konto lokalne przechodzi przez łańcuch filtrów")
    void request_whenLocalAccountCredentials_reachesApplication() throws Exception {

        // given / when / then
        mockMvc.perform(get("/auth/whoami").with(httpBasic("admin", "admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").value("admin"))
            .andExpect(jsonPath("$.authentication").value("local"));
    }

    @Test
    @DisplayName("sonda zdrowia zostaje publiczna, bo woła ją healthcheck kontenera")
    void healthProbe_whenNoCredentials_staysPublic() throws Exception {

        // given / when / then
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
