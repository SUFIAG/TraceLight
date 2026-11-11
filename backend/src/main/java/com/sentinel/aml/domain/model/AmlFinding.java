package com.sentinel.aml.domain.model;

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
 * A monitoring hit against one customer for one typology over one window.
 *
 * <p>Deduplicated on (subject, typology, window) so a pattern that persists
 * for a week produces one finding that strengthens, not seven that each need
 * clearing. Alert fatigue in AML is caused far more by repetition than by
 * sensitivity.
 */
@Entity
@Table(name = "aml_findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmlFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmlTypology typology;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String narrative;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_ids", columnDefinition = "jsonb")
    private List<String> transactionIds;

    @Column(name = "total_value", precision = 18, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    /** OPEN, ESCALATED, CLOSED_NO_ACTION, SAR_FILED. */
    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "case_id")
    private UUID caseId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
