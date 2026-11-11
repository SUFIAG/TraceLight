package com.sentinel.autonomy.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How much rope an agent has for a given action. Set per organization, per
 * action type, so a bank can run the triage agent at full autonomy while
 * keeping account freezes entirely advisory.
 */
@Getter
@RequiredArgsConstructor
public enum AutonomyLevel {

    /** The agent may not even propose this. */
    OFF(0, "Disabled"),

    /** The agent writes a recommendation; a human performs the action. */
    SUGGEST_ONLY(1, "Recommendation only"),

    /** The agent acts unaided when confidence is high and the stakes are low. */
    ACT_ON_LOW_RISK(2, "Act on low-risk items"),

    /** The agent acts within the configured value and risk limits. */
    ACT_WITHIN_LIMITS(3, "Act within limits"),

    /** The agent acts without a ceiling. Reserved for reversible actions. */
    FULL_AUTONOMY(4, "Full autonomy");

    private final int rank;
    private final String label;

    public boolean permits(AutonomyLevel required) {
        return this.rank >= required.rank;
    }
}
