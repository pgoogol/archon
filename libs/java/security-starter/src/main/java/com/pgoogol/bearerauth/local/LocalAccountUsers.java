package com.pgoogol.bearerauth.local;

import com.pgoogol.bearerauth.account.AccountAuthorities;
import com.pgoogol.bearerauth.autoconfigure.BearerAuthProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Konto z konfiguracji serwisu — jedyny użytkownik, którego serwis zna z nazwy.
 *
 * <p>Istnieje po to, żeby aplikacja dała się otworzyć bez skonfigurowanego
 * klienta Google. Hasło przychodzi wyłącznie jako hash BCrypt; jawny tekst nie
 * pojawia się ani w konfiguracji, ani w kodzie.</p>
 */
public class LocalAccountUsers {

    private final BearerAuthProperties.LocalAccount properties;

    public LocalAccountUsers(BearerAuthProperties.LocalAccount properties) {

        this.properties = properties;
    }

    public InMemoryUserDetailsManager manager() {

        UserDetails user = User.withUsername(properties.getUsername())
                .password(properties.getPasswordHash())
                .authorities(AccountAuthorities.USER)
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
