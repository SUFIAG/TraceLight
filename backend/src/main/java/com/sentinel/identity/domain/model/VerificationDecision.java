package com.sentinel.identity.domain.model;

public enum VerificationDecision {
    APPROVED,
    /** Approved but with limits — a deposit cap or a delayed payout window. */
    APPROVED_WITH_LIMITS,
    MANUAL_REVIEW,
    ADDITIONAL_EVIDENCE_REQUIRED,
    REJECTED
}
