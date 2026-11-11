package com.sentinel.graph.domain.model;

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
 * A connected component of the fraud graph that is dense enough to be worth
 * treating as one organised group rather than as unrelated customers.
 */
@Entity
@Table(name = "fraud_rings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "ring_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudRing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Stable identity across runs: a hash of the sorted member list. */
    @Column(name = "ring_key", nullable = false)
    private String ringKey;

    @Column(nullable = false)
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> members;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "link_types", nullable = false, columnDefinition = "jsonb")
    private List<String> linkTypes;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    /** Edges present divided by edges possible. Dense components are organised, sparse ones are coincidence. */
    @Column(nullable = false)
    private double density;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "total_exposure", precision = 18, scale = 2)
    private BigDecimal totalExposure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RingStatus status;

    @CreationTimestamp
    @Column(name = "detected_at", updatable = false)
    private Instant detectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
