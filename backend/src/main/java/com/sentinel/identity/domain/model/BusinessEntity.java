package com.sentinel.identity.domain.model;

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
 * A business under KYB review, with the shell-company indicators that matter:
 * age, registered-address sharing, director overlap, and ownership depth.
 */
@Entity
@Table(name = "business_entities", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "registration_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "registration_country", length = 2)
    private String registrationCountry;

    @Column(name = "incorporation_date")
    private Instant incorporationDate;

    @Column(name = "industry_code", length = 20)
    private String industryCode;

    @Column(name = "registered_address")
    private String registeredAddress;

    /** Other businesses registered at the same address. Mass-registration addresses host shells. */
    @Column(name = "address_cohabitants", nullable = false)
    private int addressCohabitants;

    @Column(name = "director_count", nullable = false)
    private int directorCount;

    /** Depth of the ownership chain before a natural person is reached. */
    @Column(name = "ownership_depth", nullable = false)
    private int ownershipDepth;

    @Column(name = "shell_score", nullable = false)
    private int shellScore;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationDecision decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> findings;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
