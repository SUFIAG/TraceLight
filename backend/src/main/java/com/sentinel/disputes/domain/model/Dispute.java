package com.sentinel.disputes.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A chargeback and the institution's response to it.
 *
 * <p>Disputes are usually handled by a different team, on a different system,
 * from the one that scored the original transaction — which is why the same
 * customer can file their fifth "I did not authorise this" of the year
 * without anybody noticing. Holding disputes beside the transaction history
 * is what makes {@code firstPartyFraudScore} possible at all.
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    /** The acquirer's or network's own case reference. */
    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "merchant_id")
    private String merchantId;

    /** VISA, MASTERCARD, AMEX, DISCOVER, SEPA, ACH. */
    @Column(nullable = false, length = 20)
    private String network;

    /** Network reason code, for example 10.4 or 13.1. */
    @Column(name = "reason_code", nullable = false, length = 20)
    private String reasonCode;

    @Column(name = "reason_description")
    private String reasonDescription;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatus status;

    /**
     * How likely the cardholder is disputing a transaction they made. High
     * scores change the economics completely: contesting is worth it.
     */
    @Column(name = "first_party_fraud_score", nullable = false)
    private int firstPartyFraudScore;

    /** Probability of winning if contested, used to decide whether to fight. */
    @Column(name = "win_probability", nullable = false)
    private int winProbability;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> evidence;

    @Column(name = "representment_text", columnDefinition = "TEXT")
    private String representmentText;

    @Column(name = "ai_drafted", nullable = false)
    private boolean aiDrafted;

    /** Network deadline. Missing it loses the case regardless of the merits. */
    @Column(name = "respond_by")
    private Instant respondBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "recovered_amount", precision = 15, scale = 2)
    private BigDecimal recoveredAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
