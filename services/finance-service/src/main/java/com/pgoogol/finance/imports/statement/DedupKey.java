package com.pgoogol.finance.imports.statement;

import com.pgoogol.finance.common.ExceptionMessageConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Klucz, po którym poznajemy, że ten sam wpis z banku widzieliśmy już wcześniej.
 *
 * <p>Gdy bank nadaje operacji własną referencję, wystarcza ona sama: jest
 * stabilna między eksportami i nie zmienia się przy przeformatowaniu opisu.</p>
 *
 * <p>Bez referencji klucz składa się z konta, daty, kwoty, znormalizowanego opisu
 * oraz <b>numeru kolejnego wśród wierszy o identycznej reszcie</b>. Bez tego
 * ostatniego składnika dwie takie same kawy za 12 zł tego samego dnia byłyby
 * jedną operacją — a to zwyczajnie się zdarza. Numer liczy się razem z wierszami
 * już zaimportowanymi, więc druga kawa z drugiego pliku dostaje numer 1, nie 0.</p>
 */
public class DedupKey {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** Klucz oparty na referencji bankowej — najmocniejszy wariant. */
    public String fromBankReference(long accountId, String bankReference) {

        Objects.requireNonNull(bankReference, "bankReference");
        return hash("%d|REF|%s".formatted(accountId, bankReference.trim()));
    }

    /**
     * Klucz zastępczy dla wyciągów bez referencji.
     *
     * @param ordinal numer kolejny wśród wierszy o identycznej reszcie klucza
     */
    public String fromContent(long accountId, LocalDate bookedOn, long amountMinor,
                              String description, int ordinal) {

        Objects.requireNonNull(bookedOn, "bookedOn");
        String normalized = normalize(description);
        return hash("%d|%s|%d|%s|%d".formatted(
            accountId, bookedOn, amountMinor, normalized, ordinal));
    }

    /**
     * Trim, redukcja ciągów białych znaków do pojedynczej spacji i wersaliki.
     * Bank potrafi wyeksportować ten sam opis raz z podwójną spacją, raz bez.
     */
    public String normalize(String description) {

        if (Objects.isNull(description)) {

            return "";
        }
        String trimmed = description.trim();
        String collapsed = SPACES.matcher(trimmed).replaceAll(" ");
        return collapsed.toUpperCase(Locale.ROOT);
    }

    private String hash(String payload) {

        MessageDigest digest = sha256();
        byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashed);
    }

    private MessageDigest sha256() {

        try {

            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(ExceptionMessageConstants.SHA_256_MISSING, ex);
        }
    }
}
