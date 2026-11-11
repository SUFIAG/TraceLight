package com.sentinel.security.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An access token handed back before it expired.
 *
 * <p>Stateless JWTs are valid until their expiry, which makes "sign out" a lie
 * unless the surrendered token is remembered. Only the identifier is kept, and
 * only until the token would have expired on its own.
 */
@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedToken {

    @Id
    @Column(name = "jti", length = 64)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    @Builder.Default
    private Instant revokedAt = Instant.now();

    @Column(name = "reason")
    private String reason;
}
