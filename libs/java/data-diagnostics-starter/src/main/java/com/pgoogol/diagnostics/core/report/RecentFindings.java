package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bufor ostatnich wniosków w pamięci, dla endpointu i eksportu. Trzyma najwyżej
 * {@code limit} wniosków; nowy wypycha najstarszy, więc pamięć jest stała niezależnie od
 * ruchu. Po restarcie aplikacji bufor jest pusty, trwały zapis to log i plik JSONL.
 */
public class RecentFindings implements FindingReporter {

    public static final int DEFAULT_LIMIT = 200;

    private final int limit;

    private final FindingFingerprint fingerprint;

    private final Deque<ReportedFinding> newestFirst = new ArrayDeque<>();

    private final ReentrantLock lock = new ReentrantLock();

    public RecentFindings(int limit, FindingFingerprint fingerprint) {

        if (limit < 1) {

            throw new IllegalArgumentException("limit ostatnich wniosków musi być dodatni: " + limit);
        }
        this.limit = limit;
        this.fingerprint = Objects.requireNonNull(fingerprint, "odcisk wniosku jest wymagany");
    }

    @Override
    public void report(UnitOfWork unit, List<Finding> findings) {

        if (findings.isEmpty()) {

            return;
        }
        UnitOfWorkSummary summary = unit.summary();
        List<ReportedFinding> reported = findings.stream()
            .map(finding -> new ReportedFinding(finding, summary, fingerprint.of(finding, summary)))
            .toList();
        lock.lock();
        try {

            reported.forEach(this::push);
        } finally {

            lock.unlock();
        }
    }

    /** Wnioski pasujące do filtra, od najnowszego. */
    public List<ReportedFinding> find(FindingQuery query) {

        lock.lock();
        try {

            return newestFirst.stream()
                .filter(query::matches)
                .toList();
        } finally {

            lock.unlock();
        }
    }

    private void push(ReportedFinding reported) {

        newestFirst.addFirst(reported);
        if (newestFirst.size() > limit) {

            newestFirst.removeLast();
        }
    }
}
