package com.sentinel.strategy.domain.model;

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
 * What a rule would have done to history.
 *
 * <p>The number that decides whether a rule ships is not its catch rate; it is
 * the trade between value caught and customers wrongly stopped. Both are
 * stored, along with the estimated operational cost of reviewing the alerts
 * the rule would have created, so the decision is made on all three.
 */
@Entity
@Table(name = "rule_backtests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleBacktest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "transactions_evaluated", nullable = false)
    private int transactionsEvaluated;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    /** Hits on transactions already flagged as fraudulent. */
    @Column(name = "true_positives", nullable = false)
    private int truePositives;

    /** Hits on transactions that were approved. These are the customers upset. */
    @Column(name = "false_positives", nullable = false)
    private int falsePositives;

    /** Flagged transactions the rule missed. */
    @Column(name = "false_negatives", nullable = false)
    private int falseNegatives;

    // "precision" is a keyword in PostgreSQL, so the column carries a suffix.
    @Column(name = "precision_score", nullable = false)
    private double precision;

    @Column(name = "recall_score", nullable = false)
    private double recall;

    @Column(name = "value_caught", precision = 18, scale = 2)
    private BigDecimal valueCaught;

    @Column(name = "value_disrupted", precision = 18, scale = 2)
    private BigDecimal valueDisrupted;

    /** Analyst hours the rule's alert volume would consume per month. */
    @Column(name = "monthly_review_hours", nullable = false)
    private double monthlyReviewHours;

    @Column(name = "window_days", nullable = false)
    private int windowDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_hits", columnDefinition = "jsonb")
    private List<String> sampleHits;

    /** SHIP, SHIP_IN_SHADOW, DO_NOT_SHIP — the recommendation, stated plainly. */
    @Column(nullable = false, length = 30)
    private String recommendation;

    @Column(name = "recommendation_reason", columnDefinition = "TEXT")
    private String recommendationReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
