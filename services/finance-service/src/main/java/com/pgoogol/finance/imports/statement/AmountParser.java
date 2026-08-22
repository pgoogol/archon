package com.pgoogol.finance.imports.statement;

import com.pgoogol.finance.common.ExceptionMessageConstants;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Kwota z wyciągu jako liczba całkowita w jednostkach podrzędnych.
 *
 * <p>Wyciągi z polskich banków piszą kwoty na kilka sposobów naraz: przecinek
 * dziesiętny, spacja jako separator tysięcy — i to nierzadko spacja niełamliwa,
 * której zwykłe {@code trim()} nie rusza — czasem kropka jako separator tysięcy,
 * czasem kod waluty doklejony na końcu. Naiwny parser wysypuje się na każdym
 * z tych wariantów z osobna.</p>
 *
 * <p>Nigdzie nie ma tu {@code double} ani {@code float}: wynik idzie przez
 * {@link BigDecimal} prosto na {@code long}, bo pół grosza zgubione na
 * zaokrągleniu binarnym wraca po roku jako rozjechane saldo.</p>
 */
public class AmountParser {

    /** Spacja zwykła, niełamliwa (U+00A0) i wąska niełamliwa (U+202F). */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u202F]");

    /** Kod waluty doklejony do kwoty: „-45,00 PLN". */
    private static final Pattern TRAILING_CURRENCY = Pattern.compile("[A-Za-z]{3}$");

    /**
     * @param minorUnit liczba miejsc po przecinku waluty; wartość spoza wyciągu
     *                  jest zaokrąglana połówkami w górę
     * @throws IllegalArgumentException gdy tekst nie jest kwotą
     */
    public long parse(String raw, int minorUnit) {

        Objects.requireNonNull(raw, "raw");
        String cleaned = clean(raw);
        if (cleaned.isEmpty()) {

            throw new IllegalArgumentException(ExceptionMessageConstants.AMOUNT_EMPTY);
        }
        BigDecimal amount = toDecimal(cleaned, raw);
        BigDecimal scaled = amount.setScale(minorUnit, RoundingMode.HALF_UP);
        BigInteger unscaled = scaled.unscaledValue();
        return unscaled.longValueExact();
    }

    /** Kwota, gdy kolumna bywa pusta — brak wartości nie jest błędem. */
    public Long parseOptional(String raw, int minorUnit) {

        if (Objects.isNull(raw) || clean(raw).isEmpty()) {

            return null;
        }
        return parse(raw, minorUnit);
    }

    private String clean(String raw) {

        String withoutSpaces = WHITESPACE.matcher(raw).replaceAll("");
        return TRAILING_CURRENCY.matcher(withoutSpaces).replaceAll("");
    }

    /**
     * Przecinek wygrywa jako separator dziesiętny: gdy w liczbie są oba znaki,
     * kropka jest separatorem tysięcy i znika.
     */
    private BigDecimal toDecimal(String cleaned, String raw) {

        String normalized = cleaned;
        if (normalized.indexOf(',') >= 0) {

            normalized = normalized.replace(".", "").replace(',', '.');
        }
        if (normalized.startsWith("+")) {

            normalized = normalized.substring(1);
        }
        try {

            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                ExceptionMessageConstants.AMOUNT_NOT_A_NUMBER.formatted(raw), ex);
        }
    }
}
