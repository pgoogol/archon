package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmUnsupportedInputException;

import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * Obraz wysyłany razem z tekstem. Format sprawdzamy przy tworzeniu, a nie przy
 * wysyłce: odbicie się od providera kosztuje przejazd po sieci i wraca
 * komunikatem o „nieprawidłowym żądaniu", z którego nic nie wynika.
 *
 * <p>Bajty są kopiowane przy tworzeniu — {@link #bytes()} zwraca wewnętrzną
 * tablicę, więc nie modyfikuj jej w miejscu.
 */
public record ImagePart(byte[] bytes, String mediaType) implements ContentPart {

    /** Formaty, które przyjmują oba providery. */
    public static final Set<String> SUPPORTED_MEDIA_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    public ImagePart {

        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
        if (bytes.length == 0) {

            throw new LlmUnsupportedInputException(LlmMessages.EMPTY_IMAGE);
        }
        if (!SUPPORTED_MEDIA_TYPES.contains(mediaType)) {

            throw new LlmUnsupportedInputException(LlmMessages.UNSUPPORTED_MEDIA_TYPE.formatted(mediaType));
        }
        bytes = bytes.clone();
    }

    /** Postać, w jakiej obraz wchodzi do żądania obu providerów. */
    public String base64() {

        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Data-URI dla providerów zgodnych z OpenAI. */
    public String dataUri() {

        return "data:%s;base64,%s".formatted(mediaType, base64());
    }
}
