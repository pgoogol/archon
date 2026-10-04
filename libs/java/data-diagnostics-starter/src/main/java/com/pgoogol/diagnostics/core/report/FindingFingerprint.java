package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Odcisk wniosku: ten sam problem w tym samym miejscu ma zawsze ten sam odcisk, więc
 * wyszukanie go w logach pokazuje każde wystąpienie.
 *
 * <p>Wniosek o jednym kształcie liczy odcisk z {@code kod|magazyn|kształt|klasa.metoda}
 * pierwszego miejsca wywołania. Numer linii nie wchodzi do odcisku, żeby dopisanie linii
 * w pliku go nie zmieniało. Wniosek o całej jednostce (żaden albo kilka kształtów) liczy
 * go z {@code kod|typ jednostki|nazwa jednostki}, bo jego najczęstszy kształt potrafi się
 * zmieniać między wywołaniami. Wynik to pierwsze 12 znaków szesnastkowych SHA-256.</p>
 */
public class FindingFingerprint {

    public static final int LENGTH = 12;

    private static final String SEPARATOR = "|";

    public String of(Finding finding, UnitOfWorkSummary unit) {

        String source = source(finding, unit);
        byte[] digest = sha256(source);
        String hex = HexFormat.of().formatHex(digest);
        return hex.substring(0, LENGTH);
    }

    private String source(Finding finding, UnitOfWorkSummary unit) {

        List<ShapeSummary> shapes = finding.shapes();
        if (shapes.size() != 1) {

            return String.join(SEPARATOR, finding.code(), unit.type().name(), unit.name());
        }
        ShapeSummary shape = shapes.getFirst();
        String caller = shape.callers().stream()
            .findFirst()
            .map(this::classAndMethod)
            .orElse("");
        return String.join(SEPARATOR, finding.code(), shape.store().name(), shape.shape(), caller);
    }

    private String classAndMethod(CallSite callSite) {

        return callSite.className() + "." + callSite.method();
    }

    private byte[] sha256(String source) {

        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException missing) {

            // każda JVM musi mieć SHA-256 (wymóg specyfikacji Java SE), więc tu nie dochodzimy
            throw new IllegalStateException("JVM bez algorytmu SHA-256", missing);
        }
    }
}
