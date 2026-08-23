package com.pgoogol.music.common;

import lombok.experimental.UtilityClass;

/**
 * Maszynowe kody błędów serwisu w jednym miejscu. Kod jest kontraktem, na którym
 * przełącza się front, więc literał wpisany z pamięci przy rzucie to kontrakt
 * napisany z pamięci — a jedna lista jest jedyną odpowiedzią na pytanie „jakie
 * kody potrafi zwrócić ten serwis", która nie jest grepem.
 *
 * <p>Zbiór rośnie razem z przenoszeniem literałów z pozostałych miejsc rzutu.</p>
 */
@UtilityClass
public class ErrorCodes {

    /** Konto Spotify właściciela nie jest połączone (brak OAuth). */
    public static final String SPOTIFY_NOT_CONNECTED = "SPOTIFY_NOT_CONNECTED";

    /** Spotify nie zna zasobu — 404. */
    public static final String SPOTIFY_RESOURCE_NOT_FOUND = "SPOTIFY_RESOURCE_NOT_FOUND";

    /** Zasób istnieje, ale użyty token nie ma do niego dostępu — 403. */
    public static final String SPOTIFY_FORBIDDEN = "SPOTIFY_FORBIDDEN";

    /** Spotify nie odpowiada albo zwraca 5xx. */
    public static final String SPOTIFY_UNAVAILABLE = "SPOTIFY_UNAVAILABLE";

    /** Chwilowe ograniczenie liczby zapytań — mija samo w kilka sekund. */
    public static final String SPOTIFY_RATE_LIMITED = "SPOTIFY_RATE_LIMITED";

    /** Wyczerpany budżet konta dewelopera — ruch wstrzymany na godziny. */
    public static final String SPOTIFY_QUOTA_EXCEEDED = "SPOTIFY_QUOTA_EXCEEDED";

    /** Błąd, którego nie potrafimy nazwać — szczegóły zostają w logach. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
