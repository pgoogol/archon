package com.pgoogol.finance.imports.statement;

import com.pgoogol.finance.common.ExceptionMessageConstants;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Wgrany plik wyciągu — nazwa i surowa zawartość, bez interpretacji.
 *
 * <p>Skrót liczymy z bajtów, nie z tekstu: ten sam plik zapisany w innym
 * kodowaniu to inny plik, a plik przekodowany po drodze przestaje być tym,
 * który wyszedł z banku.</p>
 */
public record SourceFile(String name, byte[] content) {

    public SourceFile {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {

            throw new IllegalArgumentException(ExceptionMessageConstants.SOURCE_FILE_EMPTY);
        }
    }

    /** SHA-256 zawartości, szesnastkowo — klucz odrzucania powtórnego wgrania. */
    public String fileHash() {

        MessageDigest digest = sha256();
        byte[] hashed = digest.digest(content);
        return HexFormat.of().formatHex(hashed);
    }

    public String text(Charset charset) {

        Objects.requireNonNull(charset, "charset");
        return new String(content, charset);
    }

    private MessageDigest sha256() {

        try {

            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {

            // SHA-256 jest obowiązkowy w każdej implementacji Javy — brak tego
            // algorytmu oznacza zepsute środowisko, nie sytuację do obsłużenia
            throw new IllegalStateException(ExceptionMessageConstants.SHA_256_MISSING, ex);
        }
    }
}
