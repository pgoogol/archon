package com.pgoogol.bearerauth.validation;

import com.pgoogol.bearerauth.account.AccountAuthorities;
import com.pgoogol.bearerauth.account.AllowedAccounts;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Zamienia zweryfikowany token na podmiot i decyduje o wstępie.
 *
 * <p>Świadomie nie jest walidatorem tokenu: walidator odrzuca token i kończy
 * odpowiedzią 401, a konto spoza listy ma dostać 403 — token jest przecież
 * w porządku, brakuje uprawnień. Dlatego token zawsze staje się podmiotem,
 * a uprawnienie dostaje tylko konto z listy.</p>
 */
public class AllowedAccountJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String EMAIL_CLAIM = "email";

    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final AllowedAccounts allowedAccounts;

    public AllowedAccountJwtConverter(AllowedAccounts allowedAccounts) {

        this.allowedAccounts = allowedAccounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {

        String email = source.getClaimAsString(EMAIL_CLAIM);
        String name = Objects.requireNonNullElse(email, source.getSubject());
        Collection<GrantedAuthority> authorities = resolveAuthorities(source, email);
        return new JwtAuthenticationToken(source, authorities, name);
    }

    private Collection<GrantedAuthority> resolveAuthorities(Jwt source, String email) {

        Boolean verified = source.getClaimAsBoolean(EMAIL_VERIFIED_CLAIM);
        if (!Objects.equals(Boolean.TRUE, verified)) {

            return List.of();
        }
        if (!allowedAccounts.isAllowed(email)) {

            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(AccountAuthorities.USER));
    }
}
