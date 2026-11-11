package com.sentinel.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One identity decision, with every component score kept.
 *
 * <p>Storing the components rather than only the verdict is what makes the
 * record defensible later: when a regulator or a rejected applicant asks why,
 * "document forensics 82, liveness 15, synthetic 71" is an answer, and a bare
 * REJECTED is not.
 */
@Entity
@Table(name = "identity_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The customer or applicant identifier supplied by the tenant. */
    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "subject_name")
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationDecision decision;

    /** 0-100 overall identity risk. High means likely fraudulent. */
    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    /** Forgery, tampering, and generative-model artifacts in the submitted document. */
    @Column(name = "document_score", nullable = false)
    private int documentScore;

    /** Presentation and injection attacks against the selfie check. */
    @Column(name = "liveness_score", nullable = false)
    private int livenessScore;

    /** Probability the face or voice presented was machine generated. */
    @Column(name = "deepfake_score", nullable = false)
    private int deepfakeScore;

    /** Fabricated identity assembled from real and invented attributes. */
    @Column(name = "synthetic_score", nullable = false)
    private int syntheticScore;

    /** Thin or absent digital footprint for a person of this claimed age. */
    @Column(name = "footprint_score", nullable = false)
    private int footprintScore;

    /** SIM swap, porting, and carrier-mismatch risk. */
    @Column(name = "telco_score", nullable = false)
    private int telcoScore;

    @Column(name = "sanctions_hit", nullable = false)
    private boolean sanctionsHit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "imposed_limits", columnDefinition = "jsonb")
    private List<String> imposedLimits;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /** When this identity should be looked at again. Perpetual KYC, not an annual batch. */
    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
