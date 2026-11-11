package com.sentinel.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A screening match against a sanctions, PEP, or adverse-media list.
 *
 * <p>{@code dismissedByAi} is the field that pays for this module. Name
 * screening produces enormous numbers of matches on common names, and an
 * analyst clearing "Mohammed Ali, born 1991, Manchester" against a sanctioned
 * namesake born 1962 in another country is the most wasted hour in compliance.
 */
@Entity
@Table(name = "sanctions_hits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionsHit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "verification_id")
    private UUID verificationId;

    /** SANCTIONS, PEP, ADVERSE_MEDIA, WATCHLIST. */
    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    @Column(name = "list_name", nullable = false)
    private String listName;

    @Column(name = "matched_name", nullable = false)
    private String matchedName;

    /** 0-100 string similarity between the screened name and the list entry. */
    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Column(name = "matched_dob")
    private String matchedDob;

    @Column(name = "matched_country", length = 2)
    private String matchedCountry;

    @Column(nullable = false, length = 20)
    private String status;

    /** Set when automated reasoning cleared the match, with its argument recorded. */
    @Column(name = "dismissed_by_ai", nullable = false)
    private boolean dismissedByAi;

    @Column(name = "dismissal_rationale", columnDefinition = "TEXT")
    private String dismissalRationale;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
