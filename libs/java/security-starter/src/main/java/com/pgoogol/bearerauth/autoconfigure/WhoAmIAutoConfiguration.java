package com.pgoogol.bearerauth.autoconfigure;

import com.pgoogol.bearerauth.web.WhoAmIController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint tożsamości rejestruje się niezależnie od tego, czy uwierzytelnianie
 * jest włączone.
 *
 * <p>Powód jest kontraktowy: {@code GET /auth/whoami} stoi w
 * {@code contracts/openapi/}, a test kontraktowy porównuje zbiór operacji
 * wystawianych przez kod ze zbiorem z pliku. Endpoint znikający razem
 * z profilem bez logowania rozjeżdżałby kontrakt przy każdym takim
 * uruchomieniu. Bez poświadczeń odpowiada tożsamością anonimową — i to też
 * jest prawdziwa odpowiedź na pytanie „kim jestem".</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestController.class)
public class WhoAmIAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WhoAmIController whoAmIController() {

        return new WhoAmIController();
    }
}
