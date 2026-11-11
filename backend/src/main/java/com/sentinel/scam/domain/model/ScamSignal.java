package com.sentinel.scam.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One scam-in-progress assessment for a payment attempt.
 *
 * <p>Unlike a transaction risk score, which asks "is this payment anomalous",
 * this asks "is the customer being coached right now". The customer authorized
 * the payment, so every classical authentication signal is clean.
 */
@Entity
@Table(name = "scam_signals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScamSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "beneficiary_id")
    private String beneficiaryId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScamTypology typology;

    @Column(nullable = false)
    private int score;

    /** Coaching-specific component: hesitation, live call, remote access. */
    @Column(name = "coaching_score", nullable = false)
    private int coachingScore;

    /** Beneficiary-specific component: new payee, mule exposure, crypto ramp. */
    @Column(name = "beneficiary_score", nullable = false)
    private int beneficiaryScore;

    /** Vulnerability-specific component: age, prior scam history, coercion cues. */
    @Column(name = "vulnerability_score", nullable = false)
    private int vulnerabilityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> indicators;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScamAction action;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
