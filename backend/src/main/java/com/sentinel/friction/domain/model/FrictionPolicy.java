package com.sentinel.friction.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One band in the friction ladder: a score range, a channel, and the
 * challenge that applies.
 *
 * <p>Policies are evaluated in priority order and the first match wins, so an
 * institution can express exceptions above general rules without the policy
 * set becoming a decision tree nobody can read.
 */
@Entity
@Table(name = "friction_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FrictionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    /** WEB, MOBILE, CALL_CENTRE, BRANCH, API, AGENT, ANY. */
    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "min_score", nullable = false)
    private int minScore;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FrictionMethod method;

    /** Applies only to customers under adapted protection. */
    @Column(name = "vulnerable_only", nullable = false)
    private boolean vulnerableOnly;

    /** Skipped when the device is already trusted for this customer. */
    @Column(name = "skip_if_trusted_device", nullable = false)
    private boolean skipIfTrustedDevice;

    /** Lower numbers are evaluated first. */
    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
