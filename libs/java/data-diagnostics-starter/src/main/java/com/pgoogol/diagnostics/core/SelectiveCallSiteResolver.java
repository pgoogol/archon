package com.pgoogol.diagnostics.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reguła z trybu prod: przejście po stosie kosztuje, więc miejsce wywołania ustalamy tylko
 * dla operacji wolnej i przy N-tym powtórzeniu tego samego kształtu w jednostce. To dokładnie
 * te operacje, z których powstają wnioski: wolna operacja i powtórzenia (N+1, brak batcha).
 *
 * <p>Powtórzenia liczy osobno każdy wątek, dla jednostki, w której ostatnio pracował; nowa
 * jednostka w wątku zeruje liczniki. Liczników jest najwyżej tyle, ile wynosi limit
 * kształtów, więc pamięć na wątek jest ograniczona tak samo jak w analizach.</p>
 */
public class SelectiveCallSiteResolver implements CallSiteResolver {

    private final CallSiteResolver delegate;

    private final Function<DataStore, Duration> slowThreshold;

    private final long repetition;

    private final int shapeLimit;

    private final Supplier<Optional<UnitOfWork>> currentUnit;

    private final ThreadLocal<ShapeCounts> counts = ThreadLocal.withInitial(ShapeCounts::new);

    /**
     * @param delegate      faktyczne ustalanie miejsca, zwykle {@link StackWalkingCallSiteResolver}
     * @param slowThreshold próg wolnej operacji dla magazynu
     * @param repetition    które powtórzenie kształtu w jednostce dostaje miejsce wywołania
     * @param shapeLimit    ile kształtów liczyć w jednej jednostce
     * @param currentUnit   jednostka bieżącego wątku, zwykle {@link DiagnosticsEngine#current()}
     */
    public SelectiveCallSiteResolver(CallSiteResolver delegate, Function<DataStore, Duration> slowThreshold,
                                     long repetition, int shapeLimit, Supplier<Optional<UnitOfWork>> currentUnit) {

        this.delegate = Objects.requireNonNull(delegate, "ustalanie miejsca wywołania jest wymagane");
        this.slowThreshold = Objects.requireNonNull(slowThreshold, "próg wolnej operacji jest wymagany");
        this.repetition = repetition;
        this.shapeLimit = shapeLimit;
        this.currentUnit = Objects.requireNonNull(currentUnit, "dostęp do bieżącej jednostki jest wymagany");
    }

    @Override
    public Optional<CallSite> resolve(DataStore store, String shape, Duration duration) {

        Duration threshold = slowThreshold.apply(store);
        boolean slow = duration.compareTo(threshold) >= 0;
        boolean repeated = isNthRepetition(store, shape);
        if (slow || repeated) {

            return delegate.resolve(store, shape, duration);
        }
        return Optional.empty();
    }

    private boolean isNthRepetition(DataStore store, String shape) {

        Optional<UnitOfWork> unit = currentUnit.get();
        if (unit.isEmpty()) {

            return false;
        }
        ShapeCounts unitCounts = counts.get();
        long count = unitCounts.increment(unit.get().id(), store.name() + "|" + shape, shapeLimit);
        return count == repetition;
    }

    /** Liczniki kształtów ostatniej jednostki tego wątku. */
    private static final class ShapeCounts {

        private String unitId = "";

        private final Map<String, Long> byShape = new HashMap<>();

        /** @return liczba wykonań kształtu w jednostce; 0, gdy kształt nie zmieścił się w limicie */
        long increment(String currentUnitId, String key, int limit) {

            if (!Objects.equals(unitId, currentUnitId)) {

                unitId = currentUnitId;
                byShape.clear();
            }
            if (!byShape.containsKey(key) && byShape.size() >= limit) {

                return 0;
            }
            return byShape.merge(key, 1L, Long::sum);
        }
    }
}
