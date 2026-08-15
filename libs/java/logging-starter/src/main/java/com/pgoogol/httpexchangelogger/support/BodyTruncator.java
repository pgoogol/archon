package com.pgoogol.httpexchangelogger.support;

import java.util.Objects;

public final class BodyTruncator {

    private BodyTruncator() {

    }

    public static TruncationResult truncate(String body, int maxLength) {

        if (Objects.isNull(body)) {

            return new TruncationResult(null, false);
        }
        if (maxLength <= 0) {

            return new TruncationResult("", true);
        }
        if (body.length() <= maxLength) {

            return new TruncationResult(body, false);
        }
        return new TruncationResult(body.substring(0, maxLength), true);
    }

    public record TruncationResult(String value, boolean truncated) {

    }

}
