package com.sentinel.autonomy.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The contract between an institution and its agents for one action type.
 *
 * <p>This table is the thing a risk committee signs off. Everything an agent
 * is allowed to do without a person is written here, in values the committee
 * understands: which action, up to what amount, below what risk score, above
 * what confidence.
 */
@Entity
@Table(name = "autonomy_policies", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "action_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutonomyPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AgentActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutonomyLevel level;

    /** Agents may not act unaided above this transaction value. */
    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /** Agents may not act unaided above this risk score. */
    @Column(name = "max_risk_score")
    private Integer maxRiskScore;

    /** Agents may not act unaided below this confidence in their own verdict. */
    @Column(name = "min_confidence")
    private Integer minConfidence;

    /** A second human must approve even when the agent is permitted to act. */
    @Column(name = "requires_maker_checker", nullable = false)
    private boolean requiresMakerChecker;

    /** Hard stop on how many of these an agent may take per day. */
    @Column(name = "daily_action_cap")
    private Integer dailyActionCap;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
