package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.report.ShapeSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Wspólny licznik kształtów dla analiz operacji: na każdy kształt liczba wykonań, łączny
 * i najdłuższy czas, pierwszy przykład tekstu i do trzech miejsc wywołania.
 *
 * <p>Pamięć jest ograniczona tak samo w każdej analizie: ponad {@code shapeLimit} różnych
 * kształtów kolejne nowe kształty trafiają do kubełka zbiorczego {@value #OVERFLOW_SHAPE},
 * osobnego dla każdego magazynu. Kształty już liczone liczą się dalej normalnie.</p>
 *
 * <p>Nie jest bezpieczny wątkowo: silnik woła sesje analiz pod blokadą jednostki.</p>
 */
public final class ShapeStats {

    /** Kształt kubełka zbiorczego dla operacji ponad limit kształtów. */
    public static final String OVERFLOW_SHAPE = "other";

    private final int shapeLimit;

    private final Map<ShapeKey, ShapeAccumulator> shapes = new LinkedHashMap<>();

    private final Map<DataStore, ShapeAccumulator> overflow = new LinkedHashMap<>();

    public ShapeStats(int shapeLimit) {

        if (shapeLimit < 1) {

            throw new IllegalArgumentException("limit kształtów musi być dodatni: " + shapeLimit);
        }
        this.shapeLimit = shapeLimit;
    }

    public void add(DataAccessEvent event) {

        Objects.requireNonNull(event, "zdarzenie jest wymagane");
        ShapeKey key = new ShapeKey(event.store(), event.shape());
        ShapeAccumulator accumulator = shapes.get(key);
        if (Objects.isNull(accumulator)) {

            accumulator = open(key, event.kind());
        }
        accumulator.add(event);
    }

    /** Podsumowania w kolejności pierwszego wykonania; kubełki zbiorcze na końcu. */
    public List<ShapeSummary> summaries() {

        Stream<ShapeAccumulator> regular = shapes.values().stream();
        Stream<ShapeAccumulator> buckets = overflow.values().stream();
        return Stream.concat(regular, buckets)
            .map(ShapeAccumulator::summary)
            .toList();
    }

    private ShapeAccumulator open(ShapeKey key, OperationKind kind) {

        if (shapes.size() >= shapeLimit) {

            return overflow.computeIfAbsent(key.store(), this::overflowBucket);
        }
        ShapeAccumulator created = new ShapeAccumulator(key.store(), kind, key.shape(), false);
        shapes.put(key, created);
        return created;
    }

    private ShapeAccumulator overflowBucket(DataStore store) {

        return new ShapeAccumulator(store, OperationKind.OTHER, OVERFLOW_SHAPE, true);
    }

    /** Ten sam kształt w dwóch magazynach to dwa różne kształty. */
    private record ShapeKey(DataStore store, String shape) {

    }
}
