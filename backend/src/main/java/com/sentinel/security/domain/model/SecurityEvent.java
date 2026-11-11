package com.sentinel.security.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An append-only record of something security-relevant.
 *
 * <p>Separate from the business audit trail: an investigator looking for who
 * closed a case and a responder looking for a credential-stuffing run want
 * different streams, and mixing them means both searches are slower and
 * noisier.
 */
@Entity
@Table(name = "security_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "user_id")
    private UUID userId;

    /** Kept even when no user matched, so failures against unknown addresses are visible. */
    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private SecurityEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private SecuritySeverity severity;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> detail = new HashMap<>();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
