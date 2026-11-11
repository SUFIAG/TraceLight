package com.sentinel.identity.domain.model;

public enum VerificationType {
    ONBOARDING,
    STEP_UP,
    PERIODIC_REVIEW,
    /** Triggered by a risk change rather than by a calendar date. */
    EVENT_DRIVEN,
    HIGH_VALUE_PAYMENT,
    ACCOUNT_RECOVERY
}
