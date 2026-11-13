package com.pgoogol.diagnostics.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Nazwy mierników diagnostyki i ich zapis. Rejestr przychodzi jako dostawca, bo aplikacja
 * może go nie mieć; wtedy zapis nic nie robi.
 *
 * <p>Tagi mają niską kardynalność z założenia: magazyn, rodzaj, wynik, kod, waga i typ
 * jednostki. Kształt zapytania nigdy nie jest tagiem, bo liczba kształtów nie ma górnej
 * granicy. Timery zapisane w wątku żądania ze śladem dostają egzemplarze (traceId)
 * automatycznie, gdy rejestr je wspiera, jak Prometheus przy włączonym tracingu.</p>
 */
public class DiagnosticsMeters {

    public static final String PREFIX = "data.diagnostics";

    public static final String FINDINGS = PREFIX + ".findings";

    public static final String OPERATIONS = PREFIX + ".operations";

    public static final String UNIT_OPERATIONS = PREFIX + ".unit.operations";

    public static final String TRANSACTIONS = PREFIX + ".transactions";

    public static final String CONNECTION_WAIT = PREFIX + ".connection.wait";

    public static final String UNIT_NAME_TAG = "unit.name";

    private final Supplier<@Nullable MeterRegistry> registry;

    public DiagnosticsMeters(Supplier<@Nullable MeterRegistry> registry) {

        this.registry = Objects.requireNonNull(registry, "dostęp do rejestru mierników jest wymagany");
    }

    public void operation(String store, String kind, boolean success, Duration duration) {

        timer(OPERATIONS, Tags.of("store", store, "kind", kind, "outcome", outcome(success)), duration);
    }

    public void transaction(String outcome, boolean readOnly, Duration duration) {

        timer(TRANSACTIONS, Tags.of("outcome", outcome, "read.only", String.valueOf(readOnly)), duration);
    }

    public void connectionWait(Duration duration) {

        timer(CONNECTION_WAIT, Tags.empty(), duration);
    }

    public void finding(String code, String store, String severity, String unitType) {

        MeterRegistry current = registry.get();
        if (Objects.nonNull(current)) {

            Tags tags = Tags.of("code", code, "store", store, "severity", severity, "unit.type", unitType);
            Counter.builder(FINDINGS)
                .description("Wnioski diagnostyki dostępu do danych")
                .tags(tags)
                .register(current)
                .increment();
        }
    }

    public void unitOperations(Tags tags, long operations) {

        MeterRegistry current = registry.get();
        if (Objects.nonNull(current)) {

            DistributionSummary.builder(UNIT_OPERATIONS)
                .description("Liczba operacji na magazynie w jednej jednostce pracy")
                .tags(tags)
                .register(current)
                .record(operations);
        }
    }

    private void timer(String name, Tags tags, Duration duration) {

        MeterRegistry current = registry.get();
        if (Objects.nonNull(current)) {

            Timer.builder(name)
                .tags(tags)
                .register(current)
                .record(duration);
        }
    }

    private static String outcome(boolean success) {

        if (success) {

            return "success";
        }
        return "failure";
    }
}
