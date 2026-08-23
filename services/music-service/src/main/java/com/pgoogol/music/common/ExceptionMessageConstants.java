package com.pgoogol.music.common;

import lombok.experimental.UtilityClass;

/**
 * Treści komunikatów błędów w jednym miejscu. Rozrzucone po miejscach rzutu
 * rozjeżdżają się w tonie i szczegółowości, a przeredagowanie jednego oznacza
 * polowanie po całym module. Kod błędu zostaje przy rzucie — to kontrakt dla
 * klienta; tekst jest wyłącznie dla człowieka.
 *
 * <p>Zbiór rośnie razem z przenoszeniem literałów z pozostałych miejsc rzutu.</p>
 */
@UtilityClass
public class ExceptionMessageConstants {

    public static final String SPOTIFY_NOT_CONNECTED =
        "Konto Spotify nie jest połączone — otwórz /music/api/v1/auth/spotify/login";

    /** {@code %s} — opis zasobu, np. „playlista 37i9dQ…". */
    public static final String SPOTIFY_RESOURCE_NOT_FOUND = "Spotify nie zna zasobu: %s";

    /** {@code %s} — opis zasobu, np. „playlista 37i9dQ…". */
    public static final String SPOTIFY_FORBIDDEN = "Spotify odmówił dostępu do zasobu: %s";

    public static final String SPOTIFY_UNAVAILABLE = "Spotify API niedostępne";

    public static final String SPOTIFY_RATE_LIMITED = "Spotify ograniczył liczbę zapytań";

    /** {@code %s} — moment, w którym Spotify zdejmie blokadę. */
    public static final String SPOTIFY_QUOTA_EXCEEDED =
        "Kwota Spotify wyczerpana — ruch wstrzymany do %s";

    public static final String PLAYLIST_IMPORT_FAILED =
        "nieoczekiwany błąd importu — szczegóły w logach aplikacji";
}
