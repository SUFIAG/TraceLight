package com.sentinel.disputes.domain.model;

public enum DisputeStatus {
    RECEIVED,
    EVIDENCE_GATHERING,
    REPRESENTMENT_DRAFTED,
    SUBMITTED,
    WON,
    LOST,
    /** Liability accepted without contest, because contesting costs more than the amount. */
    ACCEPTED,
    EXPIRED
}
