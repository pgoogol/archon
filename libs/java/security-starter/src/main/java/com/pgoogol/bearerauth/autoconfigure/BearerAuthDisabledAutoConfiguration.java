package com.pgoogol.bearerauth.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Łańcuch przepuszczający wszystko, dla profilu bez logowania.
 *
 * <p>Konieczny, a nie pominięty: sama obecność Spring Security na ścieżce klas
 * uruchamia jego domyślną konfigurację z formularzem logowania i hasłem
 * generowanym do logu. Wyłączenie startera musi więc znaczyć „otwarte", a nie
 * „domyślne Springa".</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnProperty(prefix = "bearer-auth", name = "enabled", havingValue = "false")
public class BearerAuthDisabledAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BearerAuthDisabledAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {

        log.warn("Uwierzytelnianie wyłączone — każde żądanie przechodzi bez poświadczeń.");
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        return http.build();
    }
}
