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
}
