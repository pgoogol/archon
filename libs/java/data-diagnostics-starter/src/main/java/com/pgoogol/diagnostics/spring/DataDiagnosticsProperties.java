package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ustawienia {@code diagnostics.*}.
 *
 * @param enabled            {@code false} wyłącza starter w całości, łącznie z owijaniem
 *                           {@code DataSource}
 * @param mode               {@code prod} (domyślnie, bezpieczny) albo {@code dev}; dev ustawia
 *                           się w profilu lokalnym
 * @param captureParameters  zapis parametrów zapytań, tylko w dev
 * @param captureOutsideUnit zdarzenia bez otwartej jednostki trafiają do wspólnej jednostki
 *                           {@code startup}/{@code background}
 * @param unitEventLimit     ile zdarzeń trzyma jednostka; puste oznacza wartość trybu
 * @param unitShapeLimit     ile kształtów analiza liczy osobno w jednostce; puste oznacza
 *                           wartość trybu
 * @param applicationPackages pakiety aplikacji dla miejsca wywołania; puste oznacza pakiet
 *                           klasy z {@code @SpringBootApplication}
 * @param analyzers          ustawienia analiz według ich identyfikatora, np. {@code n-plus-one}
 * @param output             wyjścia wniosków
 */
@ConfigurationProperties("diagnostics")
public record DataDiagnosticsProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("prod") DiagnosticsMode mode,
    boolean captureParameters,
    boolean captureOutsideUnit,
    @Nullable Integer unitEventLimit,
    @Nullable Integer unitShapeLimit,
    @DefaultValue List<String> applicationPackages,
    @DefaultValue Map<String, Analyzer> analyzers,
    @DefaultValue Output output) {

    public DataDiagnosticsProperties {

        applicationPackages = List.copyOf(applicationPackages);
        analyzers = Map.copyOf(analyzers);
    }

    /** Ustawienia rdzenia: wartości trybu, nadpisane tym, co ustawiono jawnie. */
    public DiagnosticsSettings toSettings() {

        DiagnosticsSettings settings = DiagnosticsSettings.defaults(mode)
            .withCaptureParameters(captureParameters)
            .withCaptureOutsideUnit(captureOutsideUnit);
        if (Objects.nonNull(unitEventLimit)) {

            settings = settings.withUnitEventLimit(unitEventLimit);
        }
        if (Objects.nonNull(unitShapeLimit)) {

            settings = settings.withUnitShapeLimit(unitShapeLimit);
        }
        return analyzers.entrySet().stream()
            .filter(entry -> !entry.getValue().enabled())
            .map(Map.Entry::getKey)
            .reduce(settings, (current, id) -> current.withAnalyzerEnabled(id, false), (first, second) -> second);
    }

    /** Ustawienia analizy; bez wpisu wartości domyślne. */
    public Analyzer analyzer(String id) {

        return analyzers.getOrDefault(id, Analyzer.DEFAULT);
    }

    /**
     * Ustawienia jednej analizy ({@code diagnostics.analyzers.<id>.*}). Progi są
     * w jednostkach pomiaru analizy: liczba wykonań albo milisekundy dla
     * {@code slow-operation}.
     *
     * @param enabled            {@code false} wyłącza analizę
     * @param threshold          próg; puste oznacza wartość domyślną trybu
     * @param criticalMultiplier ile razy przekroczony próg daje {@code CRITICAL}; domyślnie 5
     * @param storeThresholds    progi według nazwy magazynu, np. {@code postgresql}; używa ich
     *                           {@code slow-operation}
     */
    public record Analyzer(
        @DefaultValue("true") boolean enabled,
        @Nullable Long threshold,
        @Nullable Integer criticalMultiplier,
        @DefaultValue Map<String, Long> storeThresholds) {

        static final Analyzer DEFAULT = new Analyzer(true, null, null, Map.of());

        public Analyzer {

            storeThresholds = Map.copyOf(storeThresholds);
        }

        /** Skala progu: ustawiony próg albo {@code defaultThreshold}, ustawiony mnożnik albo 5. */
        public SeverityScale scale(long defaultThreshold) {

            long chosen = Objects.requireNonNullElse(threshold, defaultThreshold);
            return new SeverityScale(chosen, multiplier());
        }

        public int multiplier() {

            return Objects.requireNonNullElse(criticalMultiplier, SeverityScale.DEFAULT_CRITICAL_MULTIPLIER);
        }
    }

    /**
     * @param log   wnioski w logu aplikacji
     * @param jsonl wnioski w pliku JSONL
     */
    public record Output(@DefaultValue Log log, @DefaultValue Jsonl jsonl) {

    }

    /** @param enabled {@code false} wyłącza wnioski w logu */
    public record Log(@DefaultValue("true") boolean enabled) {

    }

    /**
     * @param enabled {@code true}/{@code false} jawnie; puste oznacza „tylko w dev”
     * @param path    plik wniosków
     * @param maxSize rozmiar, po którym plik przechodzi na {@code .1}
     */
    public record Jsonl(
        @Nullable Boolean enabled,
        @DefaultValue("target/data-diagnostics/findings.jsonl") Path path,
        @DefaultValue("10MB") DataSize maxSize) {

    }
}
