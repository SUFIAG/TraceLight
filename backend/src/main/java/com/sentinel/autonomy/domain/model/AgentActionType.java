package com.sentinel.autonomy.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Every action an agent can take, with the default autonomy it is allowed.
 *
 * <p>The defaults encode the principle that matters: agents may <em>stop</em>
 * work (closing a benign alert, writing a summary) far more freely than they
 * may <em>act on a customer</em> (freezing an account, refusing a payment).
 * An agent that wrongly closes an alert costs the bank a missed case; an agent
 * that wrongly freezes an account costs a real person their rent.
 */
@Getter
@RequiredArgsConstructor
public enum AgentActionType {

    CLOSE_ALERT("Close a low-risk alert", AutonomyLevel.ACT_ON_LOW_RISK),
    ESCALATE_ALERT("Escalate an alert to an analyst", AutonomyLevel.FULL_AUTONOMY),
    OPEN_CASE("Open an investigation case", AutonomyLevel.ACT_WITHIN_LIMITS),
    ASSIGN_CASE("Assign a case to an analyst", AutonomyLevel.ACT_WITHIN_LIMITS),
    SUMMARISE_CASE("Write a case summary", AutonomyLevel.FULL_AUTONOMY),
    CONTACT_CUSTOMER("Contact the customer to verify activity", AutonomyLevel.ACT_WITHIN_LIMITS),
    HOLD_PAYMENT("Place a payment on cooling-off hold", AutonomyLevel.ACT_WITHIN_LIMITS),
    RELEASE_HOLD("Release a held payment", AutonomyLevel.SUGGEST_ONLY),
    FREEZE_ACCOUNT("Freeze an account", AutonomyLevel.SUGGEST_ONLY),
    BLOCK_BENEFICIARY("Block a beneficiary account", AutonomyLevel.SUGGEST_ONLY),
    DRAFT_SAR("Draft a suspicious activity report", AutonomyLevel.ACT_WITHIN_LIMITS),
    FILE_SAR("File a suspicious activity report", AutonomyLevel.OFF),
    DEPLOY_RULE("Deploy a detection rule to production", AutonomyLevel.SUGGEST_ONLY),
    TUNE_THRESHOLD("Adjust a decision threshold", AutonomyLevel.SUGGEST_ONLY),
    FILE_DISPUTE("File a chargeback representment", AutonomyLevel.ACT_WITHIN_LIMITS);

    private final String label;
    private final AutonomyLevel defaultLevel;
}
