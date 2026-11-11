package com.sentinel.scam.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A customer flagged for adapted protection. The flag is a risk control, not a
 * label on the person: it raises friction thresholds and lowers cooling-off
 * triggers, and it is reviewable and reversible.
 */
@Entity
@Table(name = "vulnerable_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VulnerableCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** AGE, COERCION_SIGNALS, PRIOR_VICTIM, DECLARED, HEALTH. */
    @Column(nullable = false, length = 40)
    private String basis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> evidence;

    /** 1 = light touch, 3 = maximum protection. */
    @Column(name = "protection_level", nullable = false)
    private int protectionLevel;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "review_due")
    private Instant reviewDue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
