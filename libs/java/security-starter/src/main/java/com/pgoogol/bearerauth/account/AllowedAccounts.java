package com.pgoogol.bearerauth.account;

/**
 * Źródło listy kont mających wstęp do aplikacji.
 *
 * <p>Osobny typ, bo lista może przyjść skądinąd niż z konfiguracji — z pliku
 * przeładowywanego w tle albo z bazy. Podmiana źródła to wtedy jeden bean,
 * bez ruszania łańcucha filtrów.</p>
 */
public interface AllowedAccounts {

    /**
     * @param email zweryfikowany adres z tokenu, może być {@code null}
     * @return czy konto ma wstęp
     */
    boolean isAllowed(String email);
}
