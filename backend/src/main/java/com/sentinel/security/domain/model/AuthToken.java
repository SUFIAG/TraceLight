package com.sentinel.security.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-time secret: verification link, reset token, SMS code, or the handle
 * for a half-finished sign-in.
 *
 * <p>Only the hash is persisted. An attacker with read access to this table
 * still cannot complete a verification, and an operator reading the row in a
 * support session cannot impersonate the user.
 */
@Entity
@Table(name = "auth_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private AuthTokenType tokenType;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /** Where it was sent — an email address or a masked phone number. */
    @Column(name = "destination")
    private String destination;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Unused, unexpired, and still within its attempt budget. */
    public boolean isUsable() {
        return consumedAt == null
                && expiresAt.isAfter(Instant.now())
                && attempts < maxAttempts;
    }
}
