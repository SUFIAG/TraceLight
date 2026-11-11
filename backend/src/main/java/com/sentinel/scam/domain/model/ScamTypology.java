package com.sentinel.scam.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Authorized push payment (APP) scam families, aligned with the UK PSR
 * reimbursement categories and the FATF social-engineering typologies.
 *
 * <p>The typology matters as much as the score: the regulator asks which scam
 * the customer fell for, and the in-app warning that actually changes behaviour
 * is the one written for that specific scam.
 */
@Getter
@RequiredArgsConstructor
public enum ScamTypology {

    ROMANCE("Romance scam",
            "Payment to someone the customer has only met online.",
            "You have never met this person face to face. Criminals build months of trust before asking for money."),

    INVESTMENT("Investment scam",
            "Promised returns that no legitimate product can deliver.",
            "Returns this high are the single most common sign of an investment scam. No regulated firm guarantees them."),

    IMPERSONATION("Impersonation scam",
            "Caller claims to be the bank, police, or a government body.",
            "We will never ask you to move money to a 'safe account'. Neither will the police. Hang up and call us back."),

    PIG_BUTCHERING("Pig butchering",
            "Long-con crypto investment fraud with staged withdrawals.",
            "A platform that lets you withdraw small amounts first, then asks for a fee to release the rest, is a scam."),

    INVOICE_REDIRECT("Invoice redirection",
            "Supplier bank details changed shortly before payment.",
            "Call your supplier on a number you already had. Criminals intercept invoices and swap the account details."),

    PURCHASE("Purchase scam",
            "Goods paid for up front that never arrive.",
            "Paying by bank transfer for goods you have not seen leaves you with no chargeback protection."),

    ADVANCE_FEE("Advance fee scam",
            "A fee demanded to release a larger sum.",
            "Nobody legitimate needs a fee from you before paying you money."),

    SAFE_ACCOUNT("Safe account scam",
            "Customer told to move funds to protect them.",
            "No bank, police force, or regulator operates 'safe accounts'. This is always a scam."),

    CRYPTO_RAMP("Crypto off-ramp",
            "Funds pushed to an exchange or wallet under coaching.",
            "Crypto payments are irreversible. If someone is guiding you through this, stop now."),

    UNKNOWN("Unclassified",
            "Scam indicators present but no typology dominates.",
            "Something about this payment looks unusual. Take a moment before you continue.");

    private final String label;
    private final String description;
    /** Customer-facing copy. Generic warnings are ignored; specific ones are not. */
    private final String warningCopy;
}
