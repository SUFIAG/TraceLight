package com.sentinel.governance.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A model under governance.
 *
 * <p>Every supervisor now asks the same questions of any model that affects a
 * customer: what is it for, what was it trained on, who owns it, how well does
 * it work, where does it fail, and who signed it off. Those questions map
 * one-to-one onto the columns here, so the inventory is maintained as a
 * by-product of operating the model rather than reconstructed once a year for
 * an examination.
 *
 * <p>This covers the deterministic scorers and rule sets as much as any
 * learned model. Under SR 11-7 and the EU AI Act, a weighted scorecard that
 * decides whether a customer's payment is blocked is a model.
 */
@Entity
@Table(name = "model_registry", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "model_name", "model_version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelRegistryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    /** SCORECARD, RULE_SET, GRADIENT_BOOSTING, NEURAL_NETWORK, LLM_ASSISTED, HYBRID. */
    @Column(name = "model_type", nullable = false, length = 40)
    private String modelType;

    /** DEVELOPMENT, VALIDATION, APPROVED, PRODUCTION, RETIRED. */
    @Column(nullable = false, length = 20)
    private String status;

    /** EU AI Act classification: MINIMAL, LIMITED, HIGH, UNACCEPTABLE. */
    @Column(name = "risk_tier", nullable = false, length = 20)
    private String riskTier;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "training_data", columnDefinition = "TEXT")
    private String trainingData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metrics;

    /** What the model cannot do. The section examiners read first and vendors omit. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> limitations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_features", columnDefinition = "jsonb")
    private List<String> inputFeatures;

    /** Auto-generated model documentation, regenerated whenever the record changes. */
    @Column(columnDefinition = "TEXT")
    private String documentation;

    @Column(name = "validated_by")
    private String validatedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
