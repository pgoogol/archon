package com.pgoogol.httpexchangelogger.support;

import java.util.Locale;
import java.util.Objects;

public final class ContentTypeMatcher {

    private ContentTypeMatcher() {

    }

    public static boolean isLoggable(String contentType) {

        if (Objects.isNull(contentType) || contentType.isBlank()) {

            return false;
        }
        return isJson(contentType)
                || isXml(contentType)
                || isFormUrlEncoded(contentType)
                || isText(contentType);
    }

    public static boolean isJson(String contentType) {

        String base = baseType(contentType);
        if (Objects.isNull(base)) {

            return false;
        }
        if (base.equals("application/json")) {

            return true;
        }
        return base.startsWith("application/") && base.endsWith("+json");
    }

    public static boolean isXml(String contentType) {

        String base = baseType(contentType);
        if (Objects.isNull(base)) {

            return false;
        }
        if (base.equals("application/xml")) {
            return true;
        }
        return base.startsWith("application/") && base.endsWith("+xml");
    }

    public static boolean isFormUrlEncoded(String contentType) {

        String base = baseType(contentType);
        return Objects.nonNull(base) && base.equals("application/x-www-form-urlencoded");
    }

    public static boolean isText(String contentType) {

        String base = baseType(contentType);
        return Objects.nonNull(base) && base.startsWith("text/");
    }

    public static boolean isMultipart(String contentType) {

        String base = baseType(contentType);
        return Objects.nonNull(base) && base.startsWith("multipart/");
    }

    public static boolean isBinary(String contentType) {

        String base = baseType(contentType);
        if (Objects.isNull(base)) {

            return false;
        }
        if (base.startsWith("multipart/")
                || base.startsWith("image/")
                || base.startsWith("audio/")
                || base.startsWith("video/")) {
            return true;
        }
        return base.equals("application/octet-stream")
                || base.equals("application/pdf")
                || base.equals("application/zip");
    }

    private static String baseType(String contentType) {

        if (Objects.isNull(contentType)) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon);
        }
        return normalized.trim();
    }

}
