package com.pgoogol.testdiagnostics.core;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Pamięć i sprzątanie (GC) na koniec przebiegu.
 *
 * <p>Progi ocen: sterta zajęta po sprzątaniu od 70% limitu to {@link Health#WARNING},
 * od 85% {@link Health#CRITICAL} (blisko {@code OutOfMemoryError}); czas GC od 5%
 * przebiegu to {@link Health#WARNING}, od 15% {@link Health#CRITICAL}; każde pełne
 * sprzątanie to {@link Health#WARNING}, bo zatrzymuje wszystkie wątki.</p>
 *
 * @param maxHeapMb       limit sterty ({@code -Xmx})
 * @param heapPeakMb      szczyt sterty, orientacyjny: suma szczytów pul z różnych chwil, przycięta do limitu
 * @param keptAfterGcMb   najwięcej sterty zajętej zaraz po sprzątaniu; pusty, gdy GC nie ruszył
 * @param nonHeapMb       pamięć poza stertą: kod, klasy, metadane
 * @param gcMillis        łączny czas sprzątania
 * @param fullGcCount     liczba pełnych sprzątań
 */
public record MemorySnapshot(
    long maxHeapMb,
    long heapPeakMb,
    OptionalLong keptAfterGcMb,
    long nonHeapMb,
    long gcMillis,
    long fullGcCount) {

    private static final int KEPT_WARNING_PERCENT = 70;

    private static final int KEPT_CRITICAL_PERCENT = 85;

    private static final int GC_WARNING_PERCENT = 5;

    private static final int GC_CRITICAL_PERCENT = 15;

    public MemorySnapshot {

        Objects.requireNonNull(keptAfterGcMb, "pamięć po sprzątaniu jest wymagana, pusta, gdy nieznana");
    }

    public Share heapPeakShare() {

        return new Share(heapPeakMb, maxHeapMb);
    }

    public Optional<Share> keptAfterGcShare() {

        if (keptAfterGcMb.isEmpty()) {

            return Optional.empty();
        }
        Share share = new Share(keptAfterGcMb.getAsLong(), maxHeapMb);
        return Optional.of(share);
    }

    public Optional<Health> keptAfterGcHealth() {

        return keptAfterGcShare()
            .map(share -> Health.of(share, KEPT_WARNING_PERCENT, KEPT_CRITICAL_PERCENT));
    }

    public Share gcShare(long wallMillis) {

        return new Share(gcMillis, wallMillis);
    }

    public Health gcHealth(long wallMillis) {

        Share share = gcShare(wallMillis);
        return Health.of(share, GC_WARNING_PERCENT, GC_CRITICAL_PERCENT);
    }

    public Health fullGcHealth() {

        if (fullGcCount == 0) {

            return Health.OK;
        }
        return Health.WARNING;
    }
}
