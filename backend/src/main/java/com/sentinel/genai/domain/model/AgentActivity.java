package com.sentinel.genai.domain.model;

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
 * One authorisation decision for one agent request. The ledger that answers
 * "what has this agent been doing on my customer's account".
 */
@Entity
@Table(name = "ai_agent_activity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "agent_identity_id")
    private UUID agentIdentityId;

    @Column(name = "agent_key", nullable = false)
    private String agentKey;

    @Column(name = "principal_user_id")
    private String principalUserId;

    @Column(name = "requested_scope", nullable = false, length = 40)
    private String requestedScope;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    /** ALLOW, STEP_UP, DENY. */
    @Column(nullable = false, length = 20)
    private String decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
