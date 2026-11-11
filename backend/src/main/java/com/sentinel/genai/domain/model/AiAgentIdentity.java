package com.sentinel.genai.domain.model;

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
 * A registered AI agent that transacts on a customer's behalf.
 *
 * <p>Agentic commerce breaks the assumption underneath every fraud control
 * built so far: that the thing holding the credential is the person. When a
 * shopping agent pays for groceries, the behavioural signals that normally
 * separate customer from criminal — typing cadence, hesitation, session
 * rhythm — all read as "bot", because it is one.
 *
 * <p>The answer is not to detect the agent and block it; it is to know which
 * agents are authorised, by whom, for what, and up to how much. That is what
 * this record holds: a delegation, with limits, that can be revoked.
 */
@Entity
@Table(name = "ai_agent_identities", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "agent_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The identifier the agent presents on every request. */
    @Column(name = "agent_key", nullable = false)
    private String agentKey;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    /** Who operates the agent: the provider, not the customer. */
    @Column(name = "operator", nullable = false)
    private String operator;

    /** The customer who delegated authority to it. */
    @Column(name = "principal_user_id", nullable = false)
    private String principalUserId;

    /** Fingerprint of the key the agent signs its requests with. */
    @Column(name = "key_fingerprint", nullable = false)
    private String keyFingerprint;

    /** What the customer authorised: PAY, SUBSCRIBE, REFUND, READ_BALANCE, OPEN_ACCOUNT. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> scopes;

    /** Merchants or beneficiaries the agent may pay. Empty means any. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_merchants", columnDefinition = "jsonb")
    private List<String> allowedMerchants;

    @Column(name = "per_transaction_limit", precision = 15, scale = 2)
    private BigDecimal perTransactionLimit;

    @Column(name = "daily_limit", precision = 15, scale = 2)
    private BigDecimal dailyLimit;

    /** ACTIVE, SUSPENDED, REVOKED, EXPIRED. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Delegations should expire. An authority with no end date is a standing liability. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Rolling count of payments the agent has made. */
    @Column(name = "transaction_count", nullable = false)
    private long transactionCount;

    /** Rising when the agent's behaviour drifts from the delegation it was given. */
    @Column(name = "behaviour_drift_score", nullable = false)
    private int behaviourDriftScore;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
