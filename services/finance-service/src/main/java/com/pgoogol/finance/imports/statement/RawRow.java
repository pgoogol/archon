package com.pgoogol.finance.imports.statement;

import com.pgoogol.finance.common.ExceptionMessageConstants;
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

            throw new IllegalArgumentException(
                ExceptionMessageConstants.ROW_ORDINAL_NEGATIVE.formatted(ordinal));
        }
        if (amountMinor == 0) {

            throw new IllegalArgumentException(ExceptionMessageConstants.ROW_AMOUNT_ZERO);
        }
        if (Objects.isNull(originalAmountMinor) != Objects.isNull(originalCurrency)) {

            throw new IllegalArgumentException(
                ExceptionMessageConstants.ROW_ORIGINAL_INCOMPLETE);
        }
    }

    public boolean hasBankReference() {

        return Objects.nonNull(bankReference) && !bankReference.isBlank();
    }
}
