package com.sentinel.strategy.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A rule an analyst created, stored as data rather than code.
 *
 * <p>The {@code naturalLanguage} column keeps the sentence the author actually
 * wrote. Six months later, when the rule is firing oddly, the question is
 * always "what was this meant to catch", and the original sentence answers it
 * better than any reconstruction of the condition tree.
 */
@Entity
@Table(name = "rule_definitions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "name", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** The sentence the author wrote, kept verbatim. */
    @Column(name = "natural_language", columnDefinition = "TEXT")
    private String naturalLanguage;

    /** {"match":"ALL","conditions":[{"field":"amount","operator":"GT","value":5000}]} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> definition;

    /** Risk contribution when the rule fires, 0-100. */
    @Column(name = "risk_points", nullable = false)
    private int riskPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleStatus status;

    @Column(nullable = false)
    private int version;

    /** Share of traffic a challenger sees, 0-100. Ignored for other statuses. */
    @Column(name = "traffic_percent", nullable = false)
    private int trafficPercent;

    /* ---- Live performance, updated by backtests and shadow evaluation ---- */

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    @Column(name = "true_positive_count", nullable = false)
    private long truePositiveCount;

    @Column(name = "false_positive_count", nullable = false)
    private long falsePositiveCount;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
