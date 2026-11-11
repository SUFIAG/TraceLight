package com.sentinel.scam.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Session-level evidence collected by the client SDK at the moment of payment.
 * Every field is optional except the customer, because integrations adopt the
 * SDK signals incrementally and a partial assessment still beats none.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScamAssessmentRequest {

    @NotBlank
    private String userId;

    private UUID transactionId;
    private String sessionId;
    private String beneficiaryId;
    private String beneficiaryName;
    private BigDecimal amount;
    private String currency;

    /** CARD, FASTER_PAYMENTS, RTP, SEPA_INSTANT, WIRE, CRYPTO. */
    private String rail;

    /* ---- Coaching indicators ---- */

    /** Seconds the customer spent on the confirmation screen. Long pauses correlate with coaching. */
    private Integer hesitationSeconds;
    /** Device telephony reports an active call while the payment is being made. */
    private Boolean activeCallDuringSession;
    /** Screen sharing or a remote-access tool (AnyDesk, TeamViewer) is running. */
    private Boolean remoteAccessDetected;
    /** Payee fields were pasted rather than typed — the details came from somebody else. */
    private Boolean payeeDetailsPasted;
    /** Customer switched between the banking app and a messaging app during the flow. */
    private Integer appSwitchCount;
    /** Customer edited the payment reference several times, often on instruction. */
    private Integer referenceEditCount;

    /* ---- Beneficiary indicators ---- */

    private Boolean firstTimePayee;
    private Integer payeeAgeDays;
    /** Confirmation of Payee outcome: MATCH, CLOSE_MATCH, NO_MATCH, UNAVAILABLE. */
    private String confirmationOfPayee;

    /* ---- Narrative indicators ---- */

    /** Free-text payment reference or in-app chat captured with consent. */
    private String paymentReference;
    private List<String> conversationKeywords;

    /* ---- Customer context ---- */

    private Integer customerAgeYears;
    private Boolean priorScamVictim;
}
