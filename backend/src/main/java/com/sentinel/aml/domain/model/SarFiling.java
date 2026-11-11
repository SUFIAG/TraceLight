package com.sentinel.aml.domain.model;

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
 * A suspicious activity report, drafted by the platform and approved by a
 * human. The approval columns are not decoration: no jurisdiction permits a
 * machine to file on its own authority, so the workflow has to make the
 * approving officer explicit and recorded.
 */
@Entity
@Table(name = "sar_filings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SarFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "reference_number", nullable = false, unique = true)
    private String referenceNumber;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "subject_name")
    private String subjectName;

    /** FINCEN, FCA_NCA, FIU_IND, AUSTRAC, SAMA, MAS, FINTRAC. */
    @Column(nullable = false, length = 30)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SarStatus status;

    /** The regulator-facing narrative. Drafted automatically, edited by a human. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String narrative;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "finding_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> findingIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> typologies;

    @Column(name = "total_value", precision = 18, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "activity_start")
    private Instant activityStart;

    @Column(name = "activity_end")
    private Instant activityEnd;

    /** True when the narrative came from the language model rather than the template. */
    @Column(name = "ai_drafted", nullable = false)
    private boolean aiDrafted;

    @Column(name = "drafted_at")
    private Instant draftedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "filed_at")
    private Instant filedAt;

    /** Regulatory clock. Most regimes allow 30 days from detection. */
    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
