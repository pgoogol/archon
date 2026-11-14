package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.report.ShapeSummary;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Liczniki jednego kształtu w {@link ShapeStats}. Nie jest bezpieczny wątkowo. */
final class ShapeAccumulator {

    private static final int MAX_CALLERS = 3;

    private final DataStore store;

    private final OperationKind kind;

    private final String shape;

    private final boolean overflow;

    private final Set<CallSite> callers = new LinkedHashSet<>();

    private @Nullable String sample;

    private long count;

    private long failures;

    private Duration totalTime = Duration.ZERO;

    private Duration maxTime = Duration.ZERO;

    ShapeAccumulator(DataStore store, OperationKind kind, String shape, boolean overflow) {

        this.store = store;
        this.kind = kind;
        this.shape = shape;
        this.overflow = overflow;
    }

    void add(DataAccessEvent event) {

        Duration duration = event.duration();
        count++;
        if (!event.success()) {

            failures++;
        }
        totalTime = totalTime.plus(duration);
        if (duration.compareTo(maxTime) > 0) {

            maxTime = duration;
        }
        keepSample(event.text());
        keepCaller(event.callSite());
    }

    ShapeSummary summary() {

        List<CallSite> callerList = List.copyOf(callers);
        return new ShapeSummary(store, kind, shape, overflow, sample, count, failures, totalTime, maxTime, callerList);
    }

    /** Pierwszy tekst kształtu; kubełek zbiorczy nie ma przykładu, bo miesza różne zapytania. */
    private void keepSample(@Nullable String text) {

        if (Objects.isNull(sample) && !overflow) {

            sample = text;
        }
    }

    private void keepCaller(@Nullable CallSite callSite) {

        if (Objects.nonNull(callSite) && callers.size() < MAX_CALLERS) {

            callers.add(callSite);
        }
    }
}
