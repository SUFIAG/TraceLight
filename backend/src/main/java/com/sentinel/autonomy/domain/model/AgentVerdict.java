package com.sentinel.autonomy.domain.model;

public enum AgentVerdict {
    BENIGN,
    NEEDS_REVIEW,
    SUSPICIOUS,
    FRAUDULENT,
    /** The agent could not reach a view. Always routes to a human. */
    INSUFFICIENT_EVIDENCE
}
