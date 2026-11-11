package com.sentinel.friction.domain.model;

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
 * A record of every friction decision, including the ones where nothing was
 * asked of the customer.
 *
 * <p>Logging the no-friction path matters: "how many customers did we let
 * through untouched" is half of the return-on-investment story, and without
 * it the platform can only report what it stopped.
 */
@Entity
@Table(name = "friction_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FrictionDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FrictionMethod method;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "policy_name")
    private String policyName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    /** PENDING, PASSED, FAILED, ABANDONED, NOT_REQUIRED. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
