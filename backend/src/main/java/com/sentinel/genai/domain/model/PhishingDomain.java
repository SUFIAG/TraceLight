package com.sentinel.genai.domain.model;

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
 * A domain impersonating the institution's brand.
 *
 * <p>Most scam losses begin before any transaction: the customer lands on a
 * convincing clone of the bank's site. Watching for lookalike registrations
 * and getting them taken down is prevention upstream of every control the
 * platform otherwise runs.
 */
@Entity
@Table(name = "phishing_domains", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "domain"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhishingDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String domain;

    /** The protected brand term this domain imitates. */
    @Column(name = "brand_term", nullable = false)
    private String brandTerm;

    /** TYPOSQUAT, HOMOGLYPH, COMBOSQUAT, SUBDOMAIN_ABUSE, TLD_SWAP. */
    @Column(name = "technique", nullable = false, length = 30)
    private String technique;

    /** 0-100 confidence that this is deliberate impersonation. */
    @Column(name = "similarity_score", nullable = false)
    private int similarityScore;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "resolves_to_ip")
    private String resolvesToIp;

    /** A lookalike with a live login form is an active phishing site, not a parked squat. */
    @Column(name = "has_login_form", nullable = false)
    private boolean hasLoginForm;

    @Column(name = "ssl_issued", nullable = false)
    private boolean sslIssued;

    /** DETECTED, REPORTED, TAKEDOWN_REQUESTED, TAKEN_DOWN, DISMISSED. */
    @Column(nullable = false, length = 30)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> evidence;

    @Column(name = "takedown_requested_at")
    private Instant takedownRequestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
