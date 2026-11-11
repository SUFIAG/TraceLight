package com.sentinel.friction.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Step-up methods, ordered by how much they cost the customer.
 *
 * <p>{@code abandonmentRate} is the share of genuine customers who give up
 * when asked for this. It is the number missing from most friction decisions:
 * a control that stops 90% of fraud and 12% of genuine payments is usually a
 * net loss, and the only way to see that is to price the friction.
 */
@Getter
@RequiredArgsConstructor
public enum FrictionMethod {

    NONE("No additional checks", 0, 0.00),
    PASSIVE_BIOMETRIC("Silent behavioural check", 1, 0.00),
    DEVICE_BINDING("Confirm on a known device", 2, 0.02),
    PUSH_APPROVAL("Approve in the app", 3, 0.04),
    OTP_SMS("One-time code by SMS", 4, 0.07),
    BIOMETRIC("Face or fingerprint", 4, 0.05),
    KBA("Knowledge-based questions", 5, 0.14),
    VIDEO_VERIFICATION("Live video check", 6, 0.22),
    CALLBACK("Outbound call from the bank", 7, 0.18),
    BLOCK("Refuse the payment", 10, 1.00);

    private final String label;
    /** Relative burden, used to pick the lightest control that is strong enough. */
    private final int cost;
    /** Observed share of genuine customers who abandon when challenged this way. */
    private final double abandonmentRate;
}
