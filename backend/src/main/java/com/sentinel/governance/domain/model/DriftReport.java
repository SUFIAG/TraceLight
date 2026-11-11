package com.sentinel.governance.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A population stability measurement.
 *
 * <p>Models fail quietly. Nothing errors, scores keep coming out, and the
 * distribution they are drawn from has moved — because the customer base
 * changed, a new product launched, or the attackers adapted. The population
 * stability index is the standard way to see that: it compares the score
 * distribution now against the distribution the model was calibrated on.
 */
@Entity
@Table(name = "drift_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    /** What was measured: SCORE_DISTRIBUTION, FEATURE_DISTRIBUTION, OUTCOME_RATE. */
    @Column(nullable = false, length = 40)
    private String metric;

    /** Population stability index. Conventionally: <0.1 stable, 0.1-0.25 watch, >0.25 drifted. */
    @Column(nullable = false)
    private double psi;

    /** STABLE, WATCH, DRIFTED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "baseline_count", nullable = false)
    private int baselineCount;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "baseline_start")
    private Instant baselineStart;

    @Column(name = "current_start")
    private Instant currentStart;

    /** Per-bucket contribution, so the shift can be located rather than just detected. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> buckets;

    @Column(columnDefinition = "TEXT")
    private String interpretation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
