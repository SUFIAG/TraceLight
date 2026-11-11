package com.sentinel.aml.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Money-laundering typologies, mapped to the FATF descriptions the examiner
 * will use when they ask which patterns the institution monitors for.
 */
@Getter
@RequiredArgsConstructor
public enum AmlTypology {

    STRUCTURING("Structuring",
            "Payments deliberately kept below a reporting threshold.",
            "FATF R.10 / CTR avoidance"),

    RAPID_MOVEMENT("Rapid movement of funds",
            "Credits moved out almost immediately, leaving no resting balance.",
            "FATF layering"),

    ROUND_TRIPPING("Round-tripping",
            "Funds return to their origin through intermediaries.",
            "FATF layering"),

    LAYERING("Layering chain",
            "Value hops through several accounts in a short window.",
            "FATF layering"),

    HIGH_RISK_JURISDICTION("High-risk jurisdiction exposure",
            "Counterparties in jurisdictions under increased monitoring.",
            "FATF grey/black list"),

    DORMANT_REACTIVATION("Dormant account reactivation",
            "A long-idle account suddenly moves significant value.",
            "FATF placement"),

    TRADE_BASED("Trade-based laundering",
            "Payment values inconsistent with the stated trade activity.",
            "FATF TBML"),

    CASH_INTENSIVE("Cash-intensive inconsistency",
            "Turnover inconsistent with the declared business model.",
            "FATF placement"),

    THIRD_PARTY_FUNDING("Third-party funding",
            "Account funded by parties with no evident relationship to the holder.",
            "FATF placement");

    private final String label;
    private final String description;
    private final String reference;
}
