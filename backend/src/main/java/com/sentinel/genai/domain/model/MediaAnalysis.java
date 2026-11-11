package com.sentinel.genai.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One synthetic-media assessment: a call recording, a video KYC session, or a
 * document submitted as evidence.
 *
 * <p>The analysis stores signal scores, never the media itself. Voice and face
 * data are biometric personal data under most regimes, and a fraud platform
 * that accumulates a library of customer recordings has created a liability
 * considerably larger than the fraud it prevents.
 */
@Entity
@Table(name = "media_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** VOICE, VIDEO, DOCUMENT, IMAGE. */
    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(name = "subject_id")
    private String subjectId;

    /** Where the media came from: CALL_CENTRE, VIDEO_KYC, CHAT_UPLOAD, EMAIL, ONBOARDING. */
    @Column(nullable = false, length = 30)
    private String channel;

    /** Opaque reference to the media in the institution's own storage. */
    @Column(name = "media_reference")
    private String mediaReference;

    /** 0-100 probability the media was machine generated or manipulated. */
    @Column(name = "synthetic_score", nullable = false)
    private int syntheticScore;

    /** AUTHENTIC, SUSPECTED_SYNTHETIC, SYNTHETIC, INCONCLUSIVE. */
    @Column(nullable = false, length = 30)
    private String verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> indicators;

    /** What the platform did: ALLOW, STEP_UP, BLOCK, ESCALATE. */
    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "human_verdict", length = 30)
    private String humanVerdict;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
