package com.sentinel.cases.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A sampled decision pulled for quality review.
 *
 * <p>Sampling covers human and machine decisions on the same terms. An
 * institution that reviews only its agents' work and never its analysts' has
 * no baseline to judge the agents against, and the comparison is usually the
 * most useful thing the quality programme produces.
 */
@Entity
@Table(name = "qa_samples")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QaSample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** ALERT, CASE, AGENT_RUN. */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    /** HUMAN or AGENT — whose decision is being reviewed. */
    @Column(name = "decided_by_type", nullable = false, length = 10)
    private String decidedByType;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "original_decision", length = 60)
    private String originalDecision;

    /** PENDING, CORRECT, INCORRECT, PARTIALLY_CORRECT. */
    @Column(nullable = false, length = 30)
    private String verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> findings;

    @Column(columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
