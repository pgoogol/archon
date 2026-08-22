package com.pgoogol.finance.common;

import lombok.experimental.UtilityClass;

/**
 * Treści komunikatów błędów w jednym miejscu. Rozsypane po serwisach różniły się
 * tonem i szczegółowością, a przy tłumaczeniu albo zmianie brzmienia trzeba je
 * było wyszukiwać po całym module.
 *
 * <p>Klasa trzyma wyłącznie stałe — nie ma zachowania, więc nie jest klasą
 * narzędziową, której zabrania reguła stylu. Kody błędów zostają przy miejscu
 * rzucenia wyjątku: to one są kontraktem dla klienta, treść jest tylko dla
 * człowieka.</p>
 */
@UtilityClass
public class ExceptionMessageConstants {

    public static final String ACCOUNT_NOT_FOUND = "Konto %d nie istnieje";
    public static final String CATEGORY_NOT_FOUND = "Kategoria %d nie istnieje";
    public static final String TRANSACTION_NOT_FOUND = "Transakcja %d nie istnieje";
    public static final String CURRENCY_NOT_FOUND = "Waluta %s nie istnieje w słowniku";
    public static final String CURRENCY_EXISTS = "Waluta %s jest już w słowniku";

    public static final String EXCHANGE_RATE_NOT_FOUND =
        "Brak kursu %s na dzień %s ani wcześniejszego";
    public static final String BASE_CURRENCY_RATE =
        "Waluta bazowa %s nie ma kursu — z definicji wynosi 1";
    public static final String INVALID_DATE_RANGE =
        "Początek zakresu %s jest późniejszy niż koniec %s";

    public static final String CATEGORY_EXISTS =
        "Kategoria o nazwie '%s' już istnieje w tym miejscu drzewa";
    public static final String CATEGORY_PARENT_DIRECTION_MISMATCH =
        "Podkategoria musi mieć ten sam kierunek co rodzic: %s";
    public static final String CATEGORY_CYCLE =
        "Kategoria nie może być swoim własnym przodkiem";

    public static final String AMOUNT_NOT_POSITIVE =
        "Kwota musi być dodatnia — kierunek wynika z typu transakcji";
    public static final String TARGET_AMOUNT_NOT_POSITIVE =
        "Kwota po stronie docelowej musi być dodatnia";
    public static final String CURRENCY_MISMATCH =
        "Kwota jest w walucie konta — konto %s prowadzi %s, podano %s";
    public static final String ORIGINAL_AMOUNT_INCOMPLETE =
        "Kwota oryginalna i jej waluta muszą wystąpić razem albo wcale";
    public static final String TRANSFER_WITH_CATEGORY =
        "Transfer nie ma kategorii — nie jest wydatkiem ani przychodem";
    public static final String TRANSFER_WITHOUT_TARGET = "Transfer wymaga konta docelowego";
    public static final String TRANSFER_TO_SAME_ACCOUNT =
        "Transfer na to samo konto nie zmienia niczego";
    public static final String TRANSFER_TARGET_AMOUNT_REQUIRED =
        "Konta mają różne waluty (%s i %s) — podaj kwotę po stronie docelowej";
    public static final String FLOW_WITH_TRANSFER_TARGET =
        "Wydatek i przychód nie mają konta docelowego — użyj typu TRANSFER";
    public static final String CATEGORY_REQUIRED = "Wydatek i przychód wymagają kategorii";
    public static final String CATEGORY_DIRECTION_MISMATCH =
        "Kategoria '%s' jest kategorią %s, a transakcja jest typu %s";

    public static final String INVALID_PARAMETER = "Niepoprawna wartość parametru '%s': %s";
    public static final String RESOURCE_MODIFIED =
        "Wpis zmienił się w innym miejscu — odśwież i spróbuj ponownie";
    public static final String DATA_INTEGRITY_VIOLATION = "Zapis narusza spójność danych";
    public static final String INTERNAL_ERROR = "Wystąpił nieoczekiwany błąd";
    public static final String FILE_TOO_LARGE = "Przesłany plik przekracza dopuszczalny rozmiar";

    public static final String NBP_RATE_LIMITED = "NBP ograniczył liczbę zapytań";
    public static final String NBP_UNAVAILABLE = "API NBP niedostępne";

    public static final String IMPORT_BATCH_NOT_FOUND = "Partia importu %d nie istnieje";
    public static final String IMPORT_BATCH_ALREADY_COMMITTED =
        "Partia importu %d została już zatwierdzona";
    public static final String IMPORT_ROW_WITHOUT_CATEGORY =
        "Wiersz %d wyciągu nie ma przypisanej kategorii";
    public static final String STATEMENT_ALREADY_IMPORTED =
        "Plik %s został już zaimportowany na to konto";
    public static final String STATEMENT_FORMAT_UNKNOWN =
        "Nie rozpoznano formatu pliku %s";
    public static final String STATEMENT_UNREADABLE = "Nie udało się odczytać pliku %s: %s";
    public static final String STATEMENT_EMPTY = "Plik %s nie zawiera żadnej operacji";
    public static final String STATEMENT_CURRENCY_MISMATCH =
        "Wyciąg jest w walucie %s, a konto prowadzone jest w %s";
    public static final String STATEMENT_FILE_EMPTY = "Przesłany plik wyciągu jest pusty";
    public static final String IMPORT_ROW_ASSIGNED_TWICE =
        "Wiersz %d dostał dwie różne kategorie w jednym żądaniu";

    // Naruszenia niezmienników modelu. Nie wychodzą na API jako errorCode, ale
    // wychodzą do logów i do wiadomości wyjątku, więc obowiązuje ich ta sama
    // zasada: jedno miejsce, nie literał w kodzie.
    public static final String AMOUNT_MUST_BE_POSITIVE = "Kwota musi być dodatnia: %d";
    public static final String RATE_MUST_BE_POSITIVE = "Kurs musi być dodatni: %s";
    public static final String MINOR_UNIT_OUT_OF_RANGE = "Skala waluty poza zakresem 0..4: %d";
    public static final String SHA_256_MISSING = "Brak algorytmu SHA-256";
    public static final String SOURCE_FILE_EMPTY = "Plik wyciągu jest pusty";
    public static final String STATEMENT_PERIOD_REVERSED =
        "Początek okresu wyciągu jest późniejszy niż koniec: %s > %s";
    public static final String ROW_AMOUNT_ZERO = "Wiersz wyciągu na zero nie jest operacją";
    public static final String ROW_ORDINAL_NEGATIVE = "Pozycja wiersza nie może być ujemna: %d";
    public static final String ROW_ORIGINAL_INCOMPLETE =
        "Kwota oryginalna i jej waluta występują razem albo wcale";
    public static final String AMOUNT_EMPTY = "Pusta kwota";
    public static final String AMOUNT_NOT_A_NUMBER = "Nie jest kwotą: %s";
    public static final String STATEMENT_NO_TABLE_HEADER =
        "Plik nie zawiera nagłówka tabeli operacji";
    public static final String ORIGINAL_AMOUNT_WITHOUT_CURRENCY =
        "Kwota oryginalna bez kodu waluty: %s";
    public static final String DAY_OF_MONTH_OUT_OF_RANGE =
        "Dzień miesiąca poza zakresem 1..31: %d";

    public static final String RECURRING_RULE_NOT_FOUND = "Reguła cykliczna %d nie istnieje";
    public static final String OCCURRENCE_NOT_FOUND = "Pozycja terminarza %d nie istnieje";
    public static final String OCCURRENCE_ALREADY_SETTLED =
        "Pozycja terminarza %d jest już rozliczona (%s)";
    public static final String RULE_CURRENCY_MISMATCH =
        "Reguła jest w walucie konta — konto %s prowadzi %s, podano %s";
    public static final String RULE_TYPE_NOT_FLOW =
        "Reguła cykliczna opisuje wydatek albo przychód, nie %s";
    public static final String RULE_INACTIVE = "Reguła %d jest nieaktywna";
    public static final String OCCURRENCE_PAID_FROM_OTHER_CURRENCY =
        "Konto płatności prowadzi walutę %s, a pozycja terminarza jest w %s";
}
