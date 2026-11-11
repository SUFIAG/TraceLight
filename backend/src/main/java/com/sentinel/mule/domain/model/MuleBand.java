package com.sentinel.mule.domain.model;

public enum MuleBand {
    CLEAN,
    WATCH,
    SUSPECTED,
    CONFIRMED;

    public static MuleBand fromScore(int score, boolean externallyConfirmed) {
        if (externallyConfirmed) return CONFIRMED;
        if (score >= 80) return SUSPECTED;
        if (score >= 50) return WATCH;
        return CLEAN;
    }
}
