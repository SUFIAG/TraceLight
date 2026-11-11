package com.sentinel.security.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One signed-in device.
 *
 * <p>A session owns the refresh token, so signing out of a laptop does not
 * sign out of a phone, and an analyst who loses a device can end that session
 * alone. The refresh token is stored hashed and rotated on every use;
 * presenting a token that was already rotated away means the token was copied,
 * and the whole family is killed.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "refresh_token_hash", nullable = false)
    private String refreshTokenHash;

    /** The hash this session last rotated away from. A replay of it means theft. */
    @Column(name = "previous_token_hash")
    private String previousTokenHash;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Human-readable device summary, for example "Chrome on Windows". */
    @Column(name = "device_label")
    private String deviceLabel;

    /** When a second factor was last satisfied on this device. Drives step-up. */
    @Column(name = "mfa_at")
    private Instant mfaAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
