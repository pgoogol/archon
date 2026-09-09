package com.pgoogol.kitchen.dictionary.units;

import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sprowadzanie ilości do jednostek, których używa się w polskiej kuchni.
 *
 * <p>Model językowy podaje ilość i jednostkę dokładnie tak, jak stoją w źródle —
 * przeliczenie jest robotą kodu. Dzięki temu „2 cups" zamienia się w szklanki
 * zawsze tak samo, a nie raz na 480 ml, raz na 500.</p>
 *
 * <p>Klasa jest czysta: bez Springa, bez bazy, bez stanu. Cała wiedza siedzi
 * w dwóch tabelach niżej i da się ją sprawdzić testem jednostkowym.</p>
 */
public final class UnitConverter {

    /** Nazwy prowadzące wprost do jednostki ze słownika, bez przeliczania. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("g", "g"), Map.entry("gram", "g"), Map.entry("gramy", "g"),
        Map.entry("gramow", "g"), Map.entry("grams", "g"),
        Map.entry("kg", "kg"), Map.entry("kilogram", "kg"), Map.entry("kilo", "kg"),
        Map.entry("dag", "dag"), Map.entry("deka", "dag"), Map.entry("dekagram", "dag"),
        Map.entry("ml", "ml"), Map.entry("mililitr", "ml"), Map.entry("mililitry", "ml"),
        Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"),
        Map.entry("l", "l"), Map.entry("litr", "l"), Map.entry("litry", "l"),
        Map.entry("liter", "l"), Map.entry("liters", "l"), Map.entry("litre", "l"),
        Map.entry("lyzka", "lyzka"), Map.entry("lyzki", "lyzka"), Map.entry("lyzek", "lyzka"),
        Map.entry("lyzka stolowa", "lyzka"), Map.entry("tbsp", "lyzka"),
        Map.entry("tablespoon", "lyzka"), Map.entry("tablespoons", "lyzka"),
        Map.entry("lyzeczka", "lyzeczka"), Map.entry("lyzeczki", "lyzeczka"),
        Map.entry("lyzeczek", "lyzeczka"), Map.entry("tsp", "lyzeczka"),
        Map.entry("teaspoon", "lyzeczka"), Map.entry("teaspoons", "lyzeczka"),
        Map.entry("szklanka", "szklanka"), Map.entry("szklanki", "szklanka"),
        Map.entry("cup", "szklanka"), Map.entry("cups", "szklanka"),
        Map.entry("szt", "szt"), Map.entry("sztuka", "szt"), Map.entry("sztuki", "szt"),
        Map.entry("piece", "szt"), Map.entry("pieces", "szt"), Map.entry("pcs", "szt"),
        Map.entry("opakowanie", "opakowanie"), Map.entry("package", "opakowanie"),
        Map.entry("pack", "opakowanie"),
        Map.entry("puszka", "puszka"), Map.entry("can", "puszka"),
        Map.entry("sloik", "sloik"), Map.entry("jar", "sloik"),
        Map.entry("plaster", "plaster"), Map.entry("plastry", "plaster"),
        Map.entry("slice", "plaster"), Map.entry("slices", "plaster"),
        Map.entry("zabek", "zabek"), Map.entry("zabki", "zabek"), Map.entry("clove", "zabek"),
        Map.entry("cloves", "zabek"),
        Map.entry("peczek", "peczek"), Map.entry("bunch", "peczek"),
        Map.entry("kostka", "kostka"), Map.entry("listek", "listek"), Map.entry("leaf", "listek"),
        Map.entry("galazka", "galazka"), Map.entry("sprig", "galazka"),
        Map.entry("garsc", "garsc"), Map.entry("handful", "garsc"),
        Map.entry("szczypta", "szczypta"), Map.entry("pinch", "szczypta"),
        Map.entry("kropla", "kropla"), Map.entry("drop", "kropla"),
        Map.entry("do smaku", "do_smaku"), Map.entry("to taste", "do_smaku"));

    /** Jednostki obce: mnożnik i jednostka docelowa. */
    private static final Map<String, Rescale> RESCALED = Map.ofEntries(
        Map.entry("oz", new Rescale("28.35", "g")),
        Map.entry("ounce", new Rescale("28.35", "g")),
        Map.entry("ounces", new Rescale("28.35", "g")),
        Map.entry("uncja", new Rescale("28.35", "g")),
        Map.entry("lb", new Rescale("453.6", "g")),
        Map.entry("lbs", new Rescale("453.6", "g")),
        Map.entry("pound", new Rescale("453.6", "g")),
        Map.entry("pounds", new Rescale("453.6", "g")),
        Map.entry("funt", new Rescale("453.6", "g")),
        Map.entry("fl oz", new Rescale("29.57", "ml")),
        Map.entry("fl. oz", new Rescale("29.57", "ml")),
        Map.entry("fluid ounce", new Rescale("29.57", "ml")),
        Map.entry("fluid ounces", new Rescale("29.57", "ml")),
        Map.entry("pint", new Rescale("473", "ml")),
        Map.entry("pints", new Rescale("473", "ml")),
        Map.entry("quart", new Rescale("946", "ml")),
        Map.entry("quarts", new Rescale("946", "ml")),
        Map.entry("stick", new Rescale("113", "g")),
        Map.entry("sticks", new Rescale("113", "g")));

    /**
     * Jednostki zaokrąglane do liczb całkowitych. Mililitry powyżej stu idą
     * dodatkowo do pełnej piątki — 475 ml czyta się jak miarka, 473 jak wynik
     * mnożenia. Gramy zostają dokładne: 113 g masła to kostka, nie „około".
     */
    private static final Set<String> WHOLE_NUMBER_UNITS = Set.of("g", "ml");

    private static final String STEP_ROUNDED_UNIT = "ml";

    private static final int ROUNDING_STEP = 5;

    private static final BigDecimal STEP_ROUNDING_THRESHOLD = BigDecimal.valueOf(100);

    private UnitConverter() {

    }

    /**
     * Sprowadza ilość do jednostki słownikowej. Nierozpoznana jednostka nie jest
     * błędem — wraca z ostrzeżeniem i bez kodu, a wywołujący zachowuje oryginalny
     * zapis, zamiast go zgadywać.
     */
    public static UnitConversion convert(BigDecimal min, BigDecimal max, String rawUnit) {

        String key = NameNormalizer.normalize(rawUnit);
        if (Objects.isNull(key) || key.isBlank()) {

            return new UnitConversion(min, max, null, null);
        }
        String direct = ALIASES.get(key);
        if (Objects.nonNull(direct)) {

            return new UnitConversion(min, max, direct, null);
        }
        Rescale rescale = RESCALED.get(key);
        if (Objects.nonNull(rescale)) {

            return rescaled(min, max, rawUnit, rescale);
        }
        return new UnitConversion(min, max, null,
            "nieznana jednostka „%s” — zostawiono zapis ze źródła".formatted(rawUnit));
    }

    /**
     * Stopnie Fahrenheita na Celsjusza, zaokrąglone do pięciu — piekarnik i tak
     * nie odróżni 177 od 180 stopni, a okrągła liczba czyta się jak nastawa.
     */
    public static int fahrenheitToCelsius(int fahrenheit) {

        BigDecimal celsius = BigDecimal.valueOf(fahrenheit - 32)
            .multiply(BigDecimal.valueOf(5))
            .divide(BigDecimal.valueOf(9), 0, RoundingMode.HALF_UP);
        return roundToStep(celsius.intValue(), 5);
    }

    private static UnitConversion rescaled(BigDecimal min, BigDecimal max, String rawUnit,
                                           Rescale rescale) {

        BigDecimal factor = new BigDecimal(rescale.factor());
        BigDecimal newMin = scale(min, factor, rescale.target());
        BigDecimal newMax = scale(max, factor, rescale.target());
        String warning = "przeliczono z „%s” na %s — sprawdź, czy ilość ma sens"
            .formatted(rawUnit, rescale.target());
        return new UnitConversion(newMin, newMax, rescale.target(), warning);
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal factor, String target) {

        if (Objects.isNull(value)) {

            return null;
        }
        BigDecimal scaled = value.multiply(factor);
        if (!WHOLE_NUMBER_UNITS.contains(target)) {

            return scaled.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
        if (!Objects.equals(STEP_ROUNDED_UNIT, target)
            || rounded.compareTo(STEP_ROUNDING_THRESHOLD) <= 0) {

            return rounded;
        }
        return BigDecimal.valueOf(roundToStep(rounded.intValue(), ROUNDING_STEP));
    }

    private static int roundToStep(int value, int step) {

        return Math.round((float) value / step) * step;
    }

    private record Rescale(String factor, String target) {

    }
}
