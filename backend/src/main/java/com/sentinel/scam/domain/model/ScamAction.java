package com.sentinel.scam.domain.model;

/**
 * What the platform does with a scam assessment. Ordered by increasing
 * intervention, because the right answer to a scam is rarely a hard block —
 * a blocked customer is often re-coached and pays again from another channel.
 */
public enum ScamAction {
    /** Nothing. The overwhelming majority of payments land here. */
    ALLOW,
    /** Show typology-specific copy before the customer confirms. */
    WARN,
    /** Warn and require an explicit "I understand" acknowledgement. */
    WARN_AND_CONFIRM,
    /** Hold the payment for a cooling-off period. */
    COOLING_OFF,
    /** Hold and notify the customer's nominated trusted contact. */
    TRUSTED_CONTACT,
    /** Route to a human for an outbound verification call. */
    HUMAN_REVIEW,
    /** Refuse. Reserved for confirmed mule beneficiaries. */
    BLOCK
}
