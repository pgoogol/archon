package com.pgoogol.bearerauth.autoconfigure;

import com.pgoogol.bearerauth.account.AccountAuthorities;
import com.pgoogol.bearerauth.account.AllowedAccounts;
import com.pgoogol.bearerauth.account.ConfiguredAllowedAccounts;
import com.pgoogol.bearerauth.local.LocalAccountUsers;
import com.pgoogol.bearerauth.validation.AllowedAccountJwtConverter;
import com.pgoogol.bearerauth.validation.GoogleTokenValidator;
import com.pgoogol.bearerauth.web.AuthErrorWriter;
import com.pgoogol.bearerauth.web.BearerAuthAccessDeniedHandler;
import com.pgoogol.bearerauth.web.BearerAuthEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * Zamyka serwis za poświadczeniami. Dwie drogi wejścia obok siebie: token
 * identyfikacyjny Google jako Bearer oraz konto lokalne jako Basic. Filtry nie
 * wchodzą sobie w drogę — każdy reaguje wyłącznie na swój schemat w nagłówku
 * {@code Authorization}.
 *
 * <p>Wchodzi samą zależnością, bez żadnego kroku po stronie serwisu. Serwis
 * podaje tylko identyfikator klienta i listę adresów, a jeśli chce, nadpisuje
 * dowolny bean — wszystkie stoją pod {@code @ConditionalOnMissingBean}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(BearerAuthProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnProperty(prefix = "bearer-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BearerAuthAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BearerAuthAutoConfiguration.class);

    private static final int BCRYPT_STRENGTH = 12;

    private final BearerAuthProperties properties;

    public BearerAuthAutoConfiguration(BearerAuthProperties properties) {

        this.properties = properties;
        checkConfiguration();
    }

    /**
     * Serwis bez żadnej działającej drogi logowania nie ma prawa wstać.
     * Literówka w nazwie zmiennej dałaby inaczej aplikację, która wygląda na
     * zabezpieczoną, a przepuszcza każdego.
     */
    private void checkConfiguration() {

        boolean googleReady = !properties.getAudience().isBlank();
        if (googleReady && properties.getAllowedEmails().isEmpty()) {

            throw new IllegalStateException(
                    "Skonfigurowano logowanie Google, ale lista dozwolonych adresów jest pusta");
        }
        if (!googleReady && !properties.getLocalAccount().isEnabled()) {

            throw new IllegalStateException(
                    "Brak jakiejkolwiek drogi logowania: konto lokalne wyłączone, "
                            + "a identyfikator klienta Google nie jest ustawiony");
        }
        warnAboutDefaultPassword();
    }

    private void warnAboutDefaultPassword() {

        if (!properties.getLocalAccount().isEnabled()) {

            return;
        }
        String hash = properties.getLocalAccount().getPasswordHash();
        if (Objects.equals(BearerAuthProperties.DEFAULT_PASSWORD_HASH, hash)) {

            log.warn("Konto lokalne działa na haśle domyślnym. Przed wystawieniem "
                    + "aplikacji publicznie podmień hash albo wyłącz konto.");
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthErrorWriter authErrorWriter(ObjectProvider<ObjectMapper> objectMapperProvider) {

        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
        return new AuthErrorWriter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint bearerAuthEntryPoint(AuthErrorWriter errorWriter) {

        return new BearerAuthEntryPoint(errorWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessDeniedHandler bearerAuthAccessDeniedHandler(AuthErrorWriter errorWriter) {

        return new BearerAuthAccessDeniedHandler(errorWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AllowedAccounts allowedAccounts() {

        return new ConfiguredAllowedAccounts(properties.getAllowedEmails());
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "bearer-auth.local-account", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public UserDetailsService localAccountUserDetailsService() {

        LocalAccountUsers users = new LocalAccountUsers(properties.getLocalAccount());
        return users.manager();
    }

    /**
     * Dekoder tokenów Google. Rejestruje się dopiero, gdy podano identyfikator
     * klienta — bez niego nie ma czego sprawdzać w polu {@code aud}, a serwis
     * przyjmowałby tokeny wystawione dla obcych aplikacji.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "bearer-auth", name = "audience")
    public JwtDecoder jwtDecoder() {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(new GoogleTokenValidator(properties.getIssuers(), properties.getAudience()));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public Converter<Jwt, AbstractAuthenticationToken> allowedAccountJwtConverter(AllowedAccounts allowedAccounts) {

        return new AllowedAccountJwtConverter(allowedAccounts);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain bearerAuthSecurityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler,
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            ObjectProvider<Converter<Jwt, AbstractAuthenticationToken>> jwtConverterProvider) throws Exception {

        String[] publicPaths = properties.getPublicPaths().toArray(new String[0]);

        // CSRF wyłączony świadomie: API jest bezstanowe i chronione poświadczeniami
        // w nagłówku, nie ciasteczkiem sesji — token CSRF nie miałby czego bronić.
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(publicPaths).permitAll()
                .anyRequest().hasAuthority(AccountAuthorities.USER));
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        http.headers(headers -> headers
                .contentSecurityPolicy(policy -> policy.policyDirectives("default-src 'self'"))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));

        configureLocalAccount(http, entryPoint);
        configureGoogle(http, jwtDecoderProvider, jwtConverterProvider);

        return http.build();
    }

    private void configureLocalAccount(HttpSecurity http, AuthenticationEntryPoint entryPoint) throws Exception {

        if (!properties.getLocalAccount().isEnabled()) {

            return;
        }
        // własny punkt wejścia zamiast domyślnego: tamten wysyła nagłówek
        // WWW-Authenticate, na który przeglądarka odpowiada własnym oknem logowania
        http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
    }

    private void configureGoogle(HttpSecurity http,
                                 ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                 ObjectProvider<Converter<Jwt, AbstractAuthenticationToken>> jwtConverterProvider)
            throws Exception {

        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (Objects.isNull(decoder)) {

            return;
        }
        Converter<Jwt, AbstractAuthenticationToken> converter = jwtConverterProvider.getIfAvailable();
        http.oauth2ResourceServer(server -> server.jwt(jwt -> jwt
                .decoder(decoder)
                .jwtAuthenticationConverter(converter)));
    }
}
