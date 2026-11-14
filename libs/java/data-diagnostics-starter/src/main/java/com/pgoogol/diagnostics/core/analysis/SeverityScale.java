package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.report.Severity;

import java.util.Optional;

/**
 * Waga wniosku z pomiaru: poniżej progu wniosku nie ma, od progu jest {@code WARN},
 * od {@code criticalMultiplier} razy progu {@code CRITICAL}. Każda analiza operacji
 * dostaje własną skalę ze swoich ustawień.
 *
 * @param threshold          pomiar, od którego powstaje wniosek, w jednostkach analizy
 *                           (liczba wykonań albo milisekundy)
 * @param criticalMultiplier ile razy przekroczony próg daje {@code CRITICAL}
 */
public record SeverityScale(long threshold, int criticalMultiplier) {

    public static final int DEFAULT_CRITICAL_MULTIPLIER = 5;

    public SeverityScale {

        if (threshold < 1) {

            throw new IllegalArgumentException("próg analizy musi być dodatni: " + threshold);
        }
        if (criticalMultiplier < 1) {

            throw new IllegalArgumentException("mnożnik progu krytycznego musi być dodatni: " + criticalMultiplier);
        }
    }

    /** Skala z domyślnym mnożnikiem: {@code CRITICAL} od pięciokrotności progu. */
    public static SeverityScale of(long threshold) {

        return new SeverityScale(threshold, DEFAULT_CRITICAL_MULTIPLIER);
    }

    /** Waga pomiaru; pusta, gdy pomiar jest poniżej progu i wniosku nie ma. */
    public Optional<Severity> grade(long measured) {

        if (measured < threshold) {

            return Optional.empty();
        }
        if (measured < criticalThreshold()) {

            return Optional.of(Severity.WARN);
        }
        return Optional.of(Severity.CRITICAL);
    }

    /** Pomiar, od którego wniosek jest krytyczny; przy olbrzymim progu zatrzymuje się na maksimum. */
    public long criticalThreshold() {

        if (threshold > Long.MAX_VALUE / criticalMultiplier) {

            return Long.MAX_VALUE;
        }
        return threshold * criticalMultiplier;
    }
}
