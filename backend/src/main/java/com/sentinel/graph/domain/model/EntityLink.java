package com.sentinel.graph.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One undirected edge in the fraud graph.
 *
 * <p>Stored with {@code sourceId <= targetId} so the same pair can never be
 * inserted twice in opposite directions. Deduplicating at write time keeps
 * ring detection from counting one relationship as two pieces of evidence.
 */
@Entity
@Table(name = "entity_links", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "source_id", "target_id", "link_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** CUSTOMER, DEVICE, BENEFICIARY, MERCHANT, IP, EMAIL, PHONE. */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 40)
    private LinkType linkType;

    /** The shared value that created the edge, kept for the investigator's benefit. */
    @Column(name = "via_value")
    private String viaValue;

    /** Link weight times observation count, capped. Drives ring scoring. */
    @Column(nullable = false)
    private double strength;

    @Column(name = "observation_count", nullable = false)
    private int observationCount;

    @Column(name = "first_seen")
    private Instant firstSeen;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
