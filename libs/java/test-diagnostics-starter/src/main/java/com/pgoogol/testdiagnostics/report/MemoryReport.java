package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.Health;
import com.pgoogol.testdiagnostics.core.MemorySnapshot;
import com.pgoogol.testdiagnostics.core.Share;
import com.pgoogol.testdiagnostics.core.TestRunSnapshot;

/**
 * Sekcja pamięci: limit sterty, szczyt, zajętość po sprzątaniu, pamięć poza stertą
 * i sprzątanie, z ocenami OK, uwaga i krytycznie.
 */
final class MemoryReport {

    void write(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        MemorySnapshot memory = snapshot.memory();
        lines.section("section.memory");
        lines.row("memory.limit", "memory.limit.value", memory.maxHeapMb());
        Share peak = memory.heapPeakShare();
        lines.row("memory.peak", "memory.peak.value", memory.heapPeakMb(), peak.percent());
        memory.keptAfterGcShare().ifPresent(share -> keptAfterGc(memory, share, lines));
        lines.row("memory.nonHeap", "memory.nonHeap.value", memory.nonHeapMb());
        gc(snapshot, format, lines);
        Health fullGcHealth = memory.fullGcHealth();
        String fullGcStatus = status(fullGcHealth, lines);
        lines.row("memory.fullGc", "memory.fullGc.value", memory.fullGcCount(), fullGcStatus);
        lines.note("memory.note");
        lines.blank();
    }

    private void keptAfterGc(MemorySnapshot memory, Share share, ReportLines lines) {

        Health health = memory.keptAfterGcHealth().orElse(Health.OK);
        String status = status(health, lines);
        lines.row("memory.kept", "memory.kept.value", share.part(), share.percent(), status);
    }

    private void gc(TestRunSnapshot snapshot, ReportFormat format, ReportLines lines) {

        MemorySnapshot memory = snapshot.memory();
        long wall = snapshot.wallMillis();
        Share share = memory.gcShare(wall);
        Health health = memory.gcHealth(wall);
        String duration = format.duration(memory.gcMillis());
        String status = status(health, lines);
        lines.row("memory.gc", "memory.gc.value", duration, share.percent(), status);
    }

    private static String status(Health health, ReportLines lines) {

        return lines.message("health." + health.name());
    }
}
