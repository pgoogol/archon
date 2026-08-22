package com.pgoogol.finance.recurring.domain;

/**
 * Jak często wraca rachunek. Każdy wariant kotwiczy się na dniu miesiąca —
 * kwartalny i roczny to ten sam dzień co miesiąc, tylko rzadziej.
 */
public enum RecurringFrequency {

    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int monthStep;

    RecurringFrequency(int monthStep) {

        this.monthStep = monthStep;
    }

    public int monthStep() {

        return monthStep;
    }
}
