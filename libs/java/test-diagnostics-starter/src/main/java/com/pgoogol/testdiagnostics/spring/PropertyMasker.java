package com.pgoogol.testdiagnostics.spring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Właściwość z testu ({@code key=value}) w postaci do raportu. Wartość pod kluczem
 * z {@code password}, {@code secret}, {@code token} albo {@code key} znika, a za długa
 * zostaje przycięta. W obu przypadkach dochodzi krótki odcisk całej właściwości, bo
 * opis służy też do porównania konfiguracji: dwie różne wartości nie mogą wyglądać
 * na równe.
 */
final class PropertyMasker {

    private static final Pattern SECRET_KEY = Pattern.compile("(?i)password|secret|token|key");

    private static final int MAX_LENGTH = 60;

    private static final int FINGERPRINT_LENGTH = 6;

    String mask(String property) {

        int separator = separatorIndex(property);
        String key = property;
        if (separator >= 0) {

            key = property.substring(0, separator).strip();
        }
        if (separator >= 0 && SECRET_KEY.matcher(key).find()) {

            return key + "=*** #" + fingerprint(property);
        }
        if (property.length() > MAX_LENGTH) {

            return property.substring(0, MAX_LENGTH) + "... #" + fingerprint(property);
        }
        return property;
    }

    /** Pierwszy {@code =} albo {@code :}, bo Spring przyjmuje oba zapisy właściwości. */
    private static int separatorIndex(String property) {

        int equals = property.indexOf('=');
        int colon = property.indexOf(':');
        if (equals < 0) {

            return colon;
        }
        if (colon < 0) {

            return equals;
        }
        return Math.min(equals, colon);
    }

    private static String fingerprint(String property) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = property.getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(bytes);
            String hex = HexFormat.of().formatHex(hash);
            return hex.substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException missing) {

            throw new IllegalStateException("JVM bez SHA-256", missing);
        }
    }
}
