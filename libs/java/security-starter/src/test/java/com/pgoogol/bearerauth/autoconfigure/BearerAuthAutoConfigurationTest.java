package com.pgoogol.bearerauth.autoconfigure;

import com.pgoogol.bearerauth.account.AllowedAccounts;
import com.pgoogol.bearerauth.web.WhoAmIController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Warunki wpięcia startera: co się rejestruje, co da się nadpisać i kiedy
 * serwis ma się nie podnieść.
 */
class BearerAuthAutoConfigurationTest {

    private static final String AUDIENCE = "bearer-auth.audience=klient.apps.googleusercontent.com";

    private static final String ALLOWED = "bearer-auth.allowed-emails=wlasciciel@example.com";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    BearerAuthAutoConfiguration.class,
                    WhoAmIAutoConfiguration.class,
                    BearerAuthDisabledAutoConfiguration.class));

    @Test
    @DisplayName("z domyślnymi ustawieniami wpina łańcuch filtrów i konto lokalne")
    void autoConfiguration_whenDefaults_registersChainAndLocalAccount() {

        // given / when / then
        runner.run(context -> assertThat(context)
                .hasSingleBean(SecurityFilterChain.class)
                .hasSingleBean(UserDetailsService.class)
                .hasSingleBean(AllowedAccounts.class)
                .hasSingleBean(WhoAmIController.class));
    }

    @Test
    @DisplayName("bez identyfikatora klienta nie rejestruje dekodera tokenów")
    void autoConfiguration_whenAudienceMissing_doesNotRegisterJwtDecoder() {

        // given / when / then
        runner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
    }

    @Test
    @DisplayName("z identyfikatorem klienta rejestruje dekoder tokenów")
    void autoConfiguration_whenAudienceGiven_registersJwtDecoder() {

        // given / when / then
        runner.withPropertyValues(AUDIENCE, ALLOWED)
                .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
    }

    @Test
    @DisplayName("wyłączone konto lokalne znika z kontekstu")
    void autoConfiguration_whenLocalAccountDisabled_removesUserDetailsService() {

        // given / when / then
        runner.withPropertyValues("bearer-auth.local-account.enabled=false", AUDIENCE, ALLOWED)
                .run(context -> assertThat(context).doesNotHaveBean(UserDetailsService.class));
    }

    @Test
    @DisplayName("wyłączony starter zostawia łańcuch przepuszczający wszystko")
    void autoConfiguration_whenDisabled_leavesOpenChain() {

        // given / when / then
        runner.withPropertyValues("bearer-auth.enabled=false")
                .run(context -> assertThat(context)
                        .hasSingleBean(SecurityFilterChain.class)
                        .doesNotHaveBean(AllowedAccounts.class)
                        // endpoint tożsamości zostaje: pilnuje go kontrakt API
                        .hasSingleBean(WhoAmIController.class));
    }

    @Test
    @DisplayName("konfiguracja Google bez listy adresów zatrzymuje start serwisu")
    void autoConfiguration_whenAllowlistEmpty_failsToStart() {

        // given / when / then
        runner.withPropertyValues(AUDIENCE)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("lista dozwolonych adresów jest pusta"));
    }

    @Test
    @DisplayName("brak jakiejkolwiek drogi logowania zatrzymuje start serwisu")
    void autoConfiguration_whenNoLoginMethodConfigured_failsToStart() {

        // given / when / then
        runner.withPropertyValues("bearer-auth.local-account.enabled=false")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("Brak jakiejkolwiek drogi logowania"));
    }

    @Test
    @DisplayName("serwis może podmienić każdy bean startera")
    void autoConfiguration_whenServiceDefinesOwnBeans_backsOff() {

        // given / when / then
        runner.withUserConfiguration(OwnBeans.class)
                .withPropertyValues(AUDIENCE, ALLOWED)
                .run(context -> {

                    assertThat(context).hasSingleBean(AllowedAccounts.class);
                    assertThat(context.getBean(AllowedAccounts.class))
                            .isSameAs(context.getBean(OwnBeans.class).allowedAccounts);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnBeans {

        private final AllowedAccounts allowedAccounts = mock(AllowedAccounts.class);

        @Bean
        AllowedAccounts allowedAccounts() {

            return allowedAccounts;
        }
    }
}
