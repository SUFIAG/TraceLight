package com.sentinel.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One node in an ownership chain. Beneficial ownership is a tree, and the
 * number that matters — effective control — is the product of the percentages
 * along each path, not the percentage at any single level.
 */
@Entity
@Table(name = "ubo_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UboNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "business_entity_id", nullable = false)
    private UUID businessEntityId;

    /** Null for a direct owner of the root business. */
    @Column(name = "parent_node_id")
    private UUID parentNodeId;

    /** PERSON or COMPANY. The chain ends only at a person. */
    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "owner_country", length = 2)
    private String ownerCountry;

    @Column(name = "ownership_percent", nullable = false)
    private double ownershipPercent;

    /** Percentages multiplied along the path from the root. */
    @Column(name = "effective_percent", nullable = false)
    private double effectivePercent;

    @Column(nullable = false)
    private int depth;

    /** True when effective control crosses the 25% beneficial-ownership threshold. */
    @Column(name = "is_ubo", nullable = false)
    private boolean isUbo;

    @Column(name = "sanctions_screened", nullable = false)
    private boolean sanctionsScreened;

    @Column(name = "pep_flag", nullable = false)
    private boolean pepFlag;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
