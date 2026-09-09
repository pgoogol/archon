package com.pgoogol.llm.exception;

import lombok.experimental.UtilityClass;

/**
 * Treści komunikatów startera w jednym miejscu — rozsypane po miejscach rzucania
 * rozjeżdżają się w tonie i szczegółowości, a zmiana brzmienia oznacza obchód
 * całego modułu.
 */
@UtilityClass
public class LlmMessages {

    public static final String NO_CLIENT = "Klient LLM „%s” nie jest skonfigurowany — dostępne: %s";
    public static final String NO_DEFAULT_CLIENT = "Nie skonfigurowano żadnego klienta LLM (llm.clients.*)";
    public static final String AMBIGUOUS_DEFAULT_CLIENT = """
        Skonfigurowano kilka klientów LLM (%s), a żaden nie jest domyślny — \
        wskaż go w llm.default-client""";
    public static final String MISSING_API_KEY = "Klient LLM „%s” nie ma klucza API (llm.clients.%s.api-key)";
    public static final String MISSING_MODEL = "Klient LLM „%s” nie ma modelu (llm.clients.%s.model)";

    public static final String PROMPT_NOT_FOUND = "Brak zasobu promptu „%s”";
    public static final String PROMPT_UNREADABLE = "Nie udało się odczytać zasobu promptu „%s”";
    public static final String PROMPT_PLACEHOLDER_MISSING = "Prompt „%s/%s” ma nieuzupełnione miejsca: %s";
    public static final String PROMPT_WITHOUT_SCHEMA = "Prompt „%s/%s” nie ma pliku schema.json";

    public static final String RATE_LIMITED = "Provider LLM ograniczył liczbę zapytań klienta „%s”";
    public static final String UNAVAILABLE = "Provider LLM nie odpowiada dla klienta „%s”";
    public static final String REQUEST_REJECTED = "Provider LLM odrzucił żądanie klienta „%s” (HTTP %d): %s";
    public static final String RESPONSE_EMPTY = "Provider LLM zwrócił pustą odpowiedź dla klienta „%s”";
    public static final String RESPONSE_TRUNCATED = """
        Odpowiedź klienta „%s” urwał limit tokenów — treść jest niepełna, \
        zwiększ max-tokens albo skróć wejście""";
    public static final String RESPONSE_UNREADABLE = "Nie udało się odczytać odpowiedzi klienta „%s”";
    public static final String RESPONSE_NOT_MATCHING_SCHEMA =
            "Odpowiedź klienta „%s” nie odwzorowuje się na %s";

    public static final String EMPTY_IMAGE = "Obraz jest pusty";
    public static final String UNSUPPORTED_MEDIA_TYPE = "Format obrazu „%s” nie jest obsługiwany";
    public static final String EMPTY_MESSAGE = "Wiadomość bez treści";
    public static final String EMPTY_CONVERSATION = "Zapytanie bez wiadomości";
    public static final String NON_POSITIVE_MAX_TOKENS = "Limit tokenów musi być dodatni, a jest %d";
    public static final String TEMPERATURE_OUT_OF_RANGE = "Temperatura musi mieścić się w 0–2, a jest %s";
    public static final String NEGATIVE_USAGE = "Zużycie tokenów nie może być ujemne";
}
