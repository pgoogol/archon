package com.pgoogol.bearerauth.web;

import lombok.experimental.UtilityClass;

/**
 * Kody błędów uwierzytelniania — kontrakt, na którym przełącza się front.
 * Jedna lista zamiast literałów rozsypanych po klasach obsługi błędów.
 */
@UtilityClass
public class AuthErrorCodes {

    /** Brak poświadczeń albo poświadczenia nieważne — 401. */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    /** Podmiot rozpoznany, ale bez wstępu do aplikacji — 403. */
    public static final String FORBIDDEN = "FORBIDDEN";
}
