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
 * Disparate-impact testing across a segment.
 *
 * <p>Fraud models do not use protected attributes, and that is not a defence:
 * a model can produce wildly different block rates by geography, channel, or
 * customer tenure without ever seeing a protected field, and those segments
 * correlate with protected characteristics. The question a regulator asks is
 * about <em>outcomes</em>, not inputs.
 *
 * <p>The four-fifths rule is used as the screen — a group whose favourable
 * rate falls below 80% of the best group's rate is flagged for review. It is
 * a trigger for investigation, not a verdict.
 */
@Entity
@Table(name = "fairness_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FairnessReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    /** The segment tested: COUNTRY, CHANNEL, TENURE_BAND, AMOUNT_BAND. */
    @Column(nullable = false, length = 40)
    private String attribute;

    /** Per-group volume, flag rate, and ratio to the most favourably treated group. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> groups;

    /** Lowest group ratio observed. Below 0.8 fails the four-fifths screen. */
    @Column(name = "disparity_ratio", nullable = false)
    private double disparityRatio;

    /** PASS, REVIEW, FAIL. */
    @Column(nullable = false, length = 20)
    private String verdict;

    @Column(name = "groups_tested", nullable = false)
    private int groupsTested;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Column(columnDefinition = "TEXT")
    private String interpretation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
