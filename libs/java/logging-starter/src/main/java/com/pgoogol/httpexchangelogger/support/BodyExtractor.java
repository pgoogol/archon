package com.pgoogol.httpexchangelogger.support;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class BodyExtractor {

    private BodyExtractor() {

    }

    public static String toBodyString(byte[] content, String encoding) {

        if (Objects.isNull(content) || content.length == 0) {

            return null;
        }
        return new String(content, resolveCharset(encoding));
    }

    private static Charset resolveCharset(String encoding) {

        if (Objects.isNull(encoding) || encoding.isBlank()) {

            return StandardCharsets.UTF_8;
        }
        try {

            return Charset.forName(encoding);
        } catch (Exception ex) {

            return StandardCharsets.UTF_8;
        }
    }
}
