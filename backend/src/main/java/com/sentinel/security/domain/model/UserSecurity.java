package com.sentinel.security.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything about an account that is a security decision rather than a
 * profile detail: lockout counters, MFA enrolment, contact verification, and
 * credential history.
 *
 * <p>Kept beside {@code users} rather than inside it so the hot authentication
 * path reads one narrow row, and so a careless {@code SELECT *} on the user
 * table cannot leak an MFA secret.
 */
@Entity
@Table(name = "user_security")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSecurity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /* ---------- Brute-force protection ---------- */

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** How many times this account has been locked. Drives the escalating delay. */
    @Column(name = "lockout_count", nullable = false)
    @Builder.Default
    private int lockoutCount = 0;

    /* ---------- Multi-factor authentication ---------- */

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_method")
    private MfaMethod mfaMethod;

    /** Base32 TOTP seed. Never leaves the backend after enrolment completes. */
    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "mfa_enrolled_at")
    private Instant mfaEnrolledAt;

    /** SHA-256 hashes of single-use recovery codes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "backup_codes", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> backupCodes = new ArrayList<>();

    /* ---------- Contact verification ---------- */

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    /* ---------- Credential lifecycle ---------- */

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    /** Hashes of recent passwords, newest first, so one cannot be reused. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "password_history", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> passwordHistory = new ArrayList<>();

    /**
     * Stamped into every access token. Raising it rejects all tokens already
     * issued to this user on their next request, which is how "sign out
     * everywhere" takes effect without waiting for expiry.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 1;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** True while a lockout is still in force. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
