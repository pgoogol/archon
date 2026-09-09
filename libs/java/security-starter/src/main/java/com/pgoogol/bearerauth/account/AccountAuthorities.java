package com.pgoogol.bearerauth.account;

import lombok.experimental.UtilityClass;

/**
 * Uprawnienia nadawane rozpoznanym podmiotom.
 *
 * <p>Aplikacja nie ma ról — to jedno uprawnienie niesie wyłącznie odpowiedź na
 * pytanie „czy ten podmiot ma wstęp". Dzięki temu token poprawny technicznie,
 * ale wystawiony dla konta spoza listy, kończy się odmową dostępu (403), a nie
 * odrzuceniem poświadczeń (401) — w logach to różnica między obcym kontem
 * a zepsutym tokenem.</p>
 */
@UtilityClass
public class AccountAuthorities {

    public static final String USER = "ARCHON_USER";
}
