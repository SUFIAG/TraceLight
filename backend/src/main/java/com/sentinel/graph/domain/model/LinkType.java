package com.sentinel.graph.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How two entities are connected, and how much that connection is worth.
 *
 * <p>Weight is the discriminating power of the link, not its frequency. Two
 * customers on the same device are almost certainly related. Two customers
 * behind the same IP address may simply share an office or a mobile carrier's
 * NAT, so that edge is worth far less and must never carry a ring on its own.
 */
@Getter
@RequiredArgsConstructor
public enum LinkType {

    SHARED_DEVICE("Same device fingerprint", 0.90),
    SHARED_BENEFICIARY("Paid the same beneficiary", 0.55),
    SHARED_IP("Same IP address", 0.25),
    SHARED_EMAIL("Same or normalised email", 0.85),
    SHARED_PHONE("Same phone number", 0.85),
    SHARED_DOCUMENT("Same identity document", 0.95),
    PAYMENT("Direct payment", 0.40),
    SAME_RING_BEHAVIOUR("Matching behavioural fingerprint", 0.35);

    private final String label;
    private final double weight;
}
