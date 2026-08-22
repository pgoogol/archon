package com.pgoogol.finance.imports.statement;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Jeden wiersz wyciągu po sparsowaniu, przed jakąkolwiek decyzją.
 *
 * <p>Kwota jest <b>ze znakiem</b>, dokładnie tak, jak stoi na wyciągu. Zamiana
 * znaku na typ transakcji następuje w jednym miejscu, przy zatwierdzeniu partii
 * — rozsypana po parserach dawałaby tyle interpretacji, ile banków.</p>
 *
 * @param ordinal              pozycja w pliku, liczona od zera
 * @param currency             kod waluty, gdy wyciąg go podaje przy kwocie;
 *                             puste znaczy „waluta konta", nie „nieznana"
 * @param originalAmountMinor  kwota z terminala w walucie obcej, gdy wyciąg ją podaje
 * @param bankReference        identyfikator operacji nadany przez bank, gdy istnieje
 */
public record RawRow(
        int ordinal,
        LocalDate bookedOn,
        long amountMinor,
        String currency,
        Long originalAmountMinor,
        String originalCurrency,
        String description,
        String counterparty,
        String bankReference) {

    public RawRow {

        Objects.requireNonNull(bookedOn, "bookedOn");
        if (ordinal < 0) {

            throw new IllegalArgumentException("Pozycja wiersza nie może być ujemna: " + ordinal);
        }
        if (amountMinor == 0) {

            throw new IllegalArgumentException("Wiersz wyciągu na zero nie jest operacją");
        }
        if (Objects.isNull(originalAmountMinor) != Objects.isNull(originalCurrency)) {

            throw new IllegalArgumentException(
                "Kwota oryginalna i jej waluta występują razem albo wcale");
        }
    }

    public boolean hasBankReference() {

        return Objects.nonNull(bankReference) && !bankReference.isBlank();
    }
}
