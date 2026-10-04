package com.pgoogol.testdiagnostics.core;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Mierzy pamięć i sprzątanie (GC) w JVM przebiegu testów.
 *
 * <p>„Po sprzątaniu” liczy nasłuch powiadomień GC: po każdym sprzątaniu sprawdza, ile
 * sterty zostało zajęte, i trzyma maksimum. To górna granica tego, co testy naprawdę
 * trzymają w pamięci, bo po sprzątaniu samej młodej generacji w starej mogą jeszcze
 * leżeć martwe obiekty. Stan pul po ostatnim sprzątaniu
 * ({@link MemoryPoolMXBean#getCollectionUsage()}) pokazuje tylko koniec przebiegu, nie
 * najgorszą chwilę, więc służy wyłącznie za zapas, gdy JVM nie wysyła powiadomień.</p>
 *
 * <p>Powiadomienia przychodzą z osobnego wątku JVM, więc maksimum jest atomowe.</p>
 */
public final class MemoryProbe implements AutoCloseable {

    private static final int BYTES_TO_MEGABYTES_SHIFT = 20;

    private static final NotificationFilter GC_ONLY = notification -> Objects.equals(
        notification.getType(), GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);

    private final Set<String> heapPools;

    private final List<NotificationEmitter> emitters;

    private final AtomicLong peakAfterGcBytes = new AtomicLong(-1);

    private final NotificationListener listener = (notification, handback) -> onNotification(notification);

    MemoryProbe(Set<String> heapPools, List<NotificationEmitter> emitters) {

        this.heapPools = Set.copyOf(heapPools);
        this.emitters = List.copyOf(emitters);
    }

    /** Zaczyna słuchać powiadomień GC; {@link #close()} przestaje. */
    public static MemoryProbe start() {

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        Set<String> heapPools = pools.stream()
            .filter(pool -> Objects.equals(pool.getType(), MemoryType.HEAP))
            .map(MemoryPoolMXBean::getName)
            .collect(Collectors.toSet());
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        List<NotificationEmitter> emitters = collectors.stream()
            .filter(NotificationEmitter.class::isInstance)
            .map(NotificationEmitter.class::cast)
            .toList();
        MemoryProbe probe = new MemoryProbe(heapPools, emitters);
        emitters.forEach(emitter -> emitter.addNotificationListener(probe.listener, GC_ONLY, null));
        return probe;
    }

    public MemorySnapshot snapshot() {

        Runtime runtime = Runtime.getRuntime();
        long maxHeap = runtime.maxMemory();
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        long heapPeak = peakUsage(pools, MemoryType.HEAP);
        long heapPeakCapped = Math.min(heapPeak, maxHeap);
        OptionalLong keptAfterGc = keptAfterGc(pools, collectors);
        long nonHeapPeak = peakUsage(pools, MemoryType.NON_HEAP);

        long maxHeapMb = megabytes(maxHeap);
        long heapPeakMb = megabytes(heapPeakCapped);
        OptionalLong keptAfterGcMb = keptAfterGc.stream().map(MemoryProbe::megabytes).findFirst();
        long nonHeapMb = megabytes(nonHeapPeak);
        long gcMillis = gcMillis(collectors);
        long fullGcCount = fullGcCount(collectors);
        return new MemorySnapshot(maxHeapMb, heapPeakMb, keptAfterGcMb, nonHeapMb, gcMillis, fullGcCount);
    }

    @Override
    public void close() {

        emitters.forEach(this::stopListening);
    }

    /** Wołane przy każdym sprzątaniu; widoczne w pakiecie, żeby test podał użycie bez wywoływania GC. */
    void recordAfterGc(Map<String, MemoryUsage> usageAfterGc) {

        long heapUsed = usageAfterGc.entrySet().stream()
            .filter(entry -> heapPools.contains(entry.getKey()))
            .mapToLong(entry -> entry.getValue().getUsed())
            .sum();
        peakAfterGcBytes.accumulateAndGet(heapUsed, Math::max);
    }

    private void onNotification(Notification notification) {

        if (!(notification.getUserData() instanceof CompositeData data)) {

            return;
        }
        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(data);
        GcInfo gcInfo = info.getGcInfo();
        Map<String, MemoryUsage> usageAfterGc = gcInfo.getMemoryUsageAfterGc();
        recordAfterGc(usageAfterGc);
    }

    private OptionalLong keptAfterGc(List<MemoryPoolMXBean> pools, List<GarbageCollectorMXBean> collectors) {

        long peak = peakAfterGcBytes.get();
        if (peak >= 0) {

            return OptionalLong.of(peak);
        }
        long collections = collectors.stream().mapToLong(collector -> Math.max(0, collector.getCollectionCount())).sum();
        if (collections == 0) {

            return OptionalLong.empty();
        }
        long lastAfterGc = pools.stream()
            .filter(pool -> Objects.equals(pool.getType(), MemoryType.HEAP))
            .map(MemoryPoolMXBean::getCollectionUsage)
            .filter(Objects::nonNull)
            .mapToLong(MemoryUsage::getUsed)
            .sum();
        return OptionalLong.of(lastAfterGc);
    }

    private void stopListening(NotificationEmitter emitter) {

        try {

            emitter.removeNotificationListener(listener);
        } catch (ListenerNotFoundException alreadyRemoved) {

            // drugie zamknięcie: nasłuch zdjęty wcześniej, nie ma czego sprzątać
        }
    }

    private static long peakUsage(List<MemoryPoolMXBean> pools, MemoryType type) {

        return pools.stream()
            .filter(pool -> Objects.equals(pool.getType(), type))
            .map(MemoryPoolMXBean::getPeakUsage)
            .filter(Objects::nonNull)
            .mapToLong(MemoryUsage::getUsed)
            .sum();
    }

    private static long gcMillis(List<GarbageCollectorMXBean> collectors) {

        return collectors.stream().mapToLong(collector -> Math.max(0, collector.getCollectionTime())).sum();
    }

    /** Pełne sprzątanie po nazwie kolektora: G1 Old Generation, PS MarkSweep, MarkSweepCompact. */
    private static long fullGcCount(List<GarbageCollectorMXBean> collectors) {

        return collectors.stream()
            .filter(MemoryProbe::isFullCollector)
            .mapToLong(collector -> Math.max(0, collector.getCollectionCount()))
            .sum();
    }

    private static boolean isFullCollector(GarbageCollectorMXBean collector) {

        String name = collector.getName();
        return name.contains("Old") || name.contains("MarkSweep");
    }

    private static long megabytes(long bytes) {

        return bytes >> BYTES_TO_MEGABYTES_SHIFT;
    }
}
