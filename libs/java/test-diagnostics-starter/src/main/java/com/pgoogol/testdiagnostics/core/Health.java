package com.pgoogol.testdiagnostics.core;

/** Ocena wskaźnika w raporcie. */
public enum Health {

    OK,

    WARNING,

    CRITICAL;

    /**
     * @param share           mierzony udział
     * @param warningPercent  od tylu procent włącznie {@link #WARNING}
     * @param criticalPercent od tylu procent włącznie {@link #CRITICAL}
     */
    public static Health of(Share share, int warningPercent, int criticalPercent) {

        int percent = share.percent();
        if (percent >= criticalPercent) {

            return CRITICAL;
        }
        if (percent >= warningPercent) {

            return WARNING;
        }
        return OK;
    }
}
