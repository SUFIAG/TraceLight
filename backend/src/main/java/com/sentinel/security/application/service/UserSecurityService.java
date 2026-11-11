package com.sentinel.security.application.service;

import com.sentinel.auth.domain.model.User;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.adapter.out.persistence.UserSecurityRepository;
import com.sentinel.security.domain.model.SecurityEventType;
import com.sentinel.security.domain.model.UserSecurity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the lockout decision and the per-account security row.
 *
 * <p>Three wrong passwords lock the account for fifteen minutes, and every
 * lockout after that doubles the wait up to a day. The doubling is the point:
 * a person who mistypes twice is barely inconvenienced, while an automated
 * guesser is pushed from thousands of attempts an hour to a handful.
 *
 * <p>Failures older than the configured window are forgiven, so somebody who
 * fumbles a password in the morning is not one slip from a lockout at night.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSecurityService {

    private final UserSecurityRepository repository;
    private final SecurityEventService securityEvents;
    private final SecurityProperties properties;

    /** Reads the posture row, creating it the first time an account needs one. */
    @Transactional
    public UserSecurity forUser(User user) {
        return repository.findById(user.getId()).orElseGet(() -> repository.save(
                UserSecurity.builder()
                        .userId(user.getId())
                        .organizationId(user.getOrganizationId())
                        .passwordChangedAt(user.getCreatedAt() == null ? Instant.now() : user.getCreatedAt())
                        .build()));
    }

    @Transactional(readOnly = true)
    public UserSecurity require(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new BusinessException("NO_SECURITY_PROFILE",
                        "This account has no security profile yet. Sign in once to create it."));
    }

    public UserSecurity save(UserSecurity security) {
        return repository.save(security);
    }

    /**
     * Refuses the sign-in when the account is locked, and says how long is
     * left. Naming the wait is deliberate: a user who is told only "invalid
     * credentials" will keep trying and extend their own lockout.
     */
    public void assertNotLocked(UserSecurity security, String email, HttpServletRequest request) {
        if (!security.isLocked()) return;
        long minutes = Math.max(1, Duration.between(Instant.now(), security.getLockedUntil()).toMinutes());
        securityEvents.record(SecurityEventType.LOGIN_BLOCKED_LOCKED, security.getOrganizationId(),
                security.getUserId(), email, request, Map.of("minutesRemaining", minutes));
        throw new BusinessException("ACCOUNT_LOCKED",
                "Too many failed sign-in attempts. Try again in " + minutes
                        + (minutes == 1 ? " minute" : " minutes") + ", or reset your password.");
    }

    /** What a failed attempt cost: either a warning, or a lockout with its length. */
    public record FailureOutcome(boolean locked, long lockoutMinutes, int attemptsRemaining) {
    }

    /**
     * Counts a wrong password and locks the account once the budget is spent.
     *
     * <p>Runs in its own transaction, and returns the outcome rather than
     * throwing it. The caller's transaction is about to roll back — a failed
     * sign-in is a failure — and a counter that rolls back with it would never
     * reach the threshold, leaving the lockout permanently one attempt away.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureOutcome recordFailure(UserSecurity security, String email, HttpServletRequest request) {
        Instant now = Instant.now();
        boolean windowExpired = security.getLastFailedAt() != null
                && security.getLastFailedAt().isBefore(
                now.minus(Duration.ofMinutes(properties.getFailedAttemptWindowMinutes())));

        int attempts = (windowExpired ? 0 : security.getFailedAttempts()) + 1;
        security.setFailedAttempts(attempts);
        security.setLastFailedAt(now);

        if (attempts >= properties.getMaxFailedAttempts()) {
            int lockoutNumber = security.getLockoutCount() + 1;
            long minutes = Math.min(
                    (long) properties.getMaxLockoutMinutes(),
                    (long) properties.getLockoutMinutes() * (1L << Math.min(6, lockoutNumber - 1)));
            security.setLockedUntil(now.plus(Duration.ofMinutes(minutes)));
            security.setLockoutCount(lockoutNumber);
            security.setFailedAttempts(0);
            repository.save(security);

            securityEvents.record(SecurityEventType.ACCOUNT_LOCKED, security.getOrganizationId(),
                    security.getUserId(), email, request,
                    Map.of("lockoutMinutes", minutes, "lockoutNumber", lockoutNumber));
            return new FailureOutcome(true, minutes, 0);
        }

        repository.save(security);
        int remaining = properties.getMaxFailedAttempts() - attempts;
        securityEvents.record(SecurityEventType.LOGIN_FAILED, security.getOrganizationId(),
                security.getUserId(), email, request, Map.of("attemptsRemaining", remaining));
        return new FailureOutcome(false, 0, remaining);
    }

    /** The message for a failed attempt, including a lockout when one just started. */
    public BusinessException failureException(FailureOutcome outcome) {
        if (outcome.locked()) {
            return new BusinessException("ACCOUNT_LOCKED",
                    "Too many failed sign-in attempts. This account is locked for "
                            + outcome.lockoutMinutes()
                            + (outcome.lockoutMinutes() == 1 ? " minute" : " minutes") + ".");
        }
        return new BusinessException("INVALID_CREDENTIALS", "Invalid email or password");
    }

    /** Clears the counters after a successful sign-in. */
    @Transactional
    public void recordSuccess(UserSecurity security) {
        if (security.getFailedAttempts() == 0 && security.getLockedUntil() == null) return;
        security.setFailedAttempts(0);
        security.setLockedUntil(null);
        repository.save(security);
    }

    /** Administrator override: clears a lockout without waiting it out. */
    @Transactional
    public UserSecurity unlock(UUID userId, UUID actorId, HttpServletRequest request) {
        UserSecurity security = require(userId);
        security.setLockedUntil(null);
        security.setFailedAttempts(0);
        security.setLockoutCount(0);
        repository.save(security);
        securityEvents.record(SecurityEventType.ACCOUNT_UNLOCKED, security.getOrganizationId(),
                userId, null, request, Map.of("unlockedBy", String.valueOf(actorId)));
        return security;
    }

    /**
     * Invalidates every access token already issued to this user.
     *
     * <p>Used after a password change, an MFA change, or a role change. Without
     * it, a token minted before the change keeps the old permissions until it
     * expires — which is exactly the window an attacker needs.
     */
    @Transactional
    public void bumpTokenVersion(UUID userId) {
        repository.findById(userId).ifPresent(security -> {
            security.setTokenVersion(security.getTokenVersion() + 1);
            repository.save(security);
        });
    }

    /** True when the account must change its password before doing anything else. */
    public boolean passwordExpired(UserSecurity security) {
        if (properties.getPasswordMaxAgeDays() <= 0) return false;
        if (security.getPasswordChangedAt() == null) return false;
        return security.getPasswordChangedAt()
                .isBefore(Instant.now().minus(Duration.ofDays(properties.getPasswordMaxAgeDays())));
    }
}
