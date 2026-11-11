package com.sentinel.security.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.sentinel.security.domain.model.SecuritySeverity.*;

/**
 * The security-relevant things that can happen to an account.
 *
 * <p>Each carries its own severity so a SIEM can alert on the serious ones
 * without the platform having to classify them at write time. The set is
 * deliberately small: an event nobody would act on is noise that hides the
 * events somebody would.
 */
@Getter
@RequiredArgsConstructor
public enum SecurityEventType {

    LOGIN_SUCCEEDED("Signed in", INFO),
    LOGIN_FAILED("Wrong password", LOW),
    LOGIN_BLOCKED_LOCKED("Sign-in refused, account locked", MEDIUM),
    LOGIN_BLOCKED_DISABLED("Sign-in refused, account disabled", MEDIUM),
    ACCOUNT_LOCKED("Account locked after repeated failures", HIGH),
    ACCOUNT_UNLOCKED("Account unlocked", MEDIUM),

    MFA_CHALLENGED("Second factor requested", INFO),
    MFA_SUCCEEDED("Second factor accepted", INFO),
    MFA_FAILED("Second factor rejected", MEDIUM),
    MFA_ENABLED("Multi-factor authentication enabled", INFO),
    MFA_DISABLED("Multi-factor authentication disabled", HIGH),
    BACKUP_CODE_USED("Recovery code used", MEDIUM),
    BACKUP_CODES_REGENERATED("Recovery codes regenerated", MEDIUM),

    EMAIL_VERIFICATION_SENT("Verification email sent", INFO),
    EMAIL_VERIFIED("Email verified", INFO),
    PHONE_VERIFICATION_SENT("Verification code sent by text", INFO),
    PHONE_VERIFIED("Phone verified", INFO),

    PASSWORD_CHANGED("Password changed", MEDIUM),
    PASSWORD_RESET_REQUESTED("Password reset requested", MEDIUM),
    PASSWORD_RESET_COMPLETED("Password reset completed", HIGH),

    SESSION_CREATED("New device signed in", INFO),
    SESSION_REVOKED("Session ended", INFO),
    ALL_SESSIONS_REVOKED("All sessions ended", MEDIUM),
    REFRESH_TOKEN_REUSED("Refresh token replayed, all sessions ended", CRITICAL),

    RATE_LIMIT_TRIPPED("Too many requests", MEDIUM),
    STEP_UP_REQUIRED("Sensitive action needs re-authentication", INFO),
    STEP_UP_FAILED("Sensitive action refused", HIGH),

    ROLE_CHANGED("Role changed", HIGH),
    USER_DISABLED("Account disabled by an administrator", HIGH),
    USER_ENABLED("Account enabled by an administrator", MEDIUM),
    USER_INVITED("Account created by an administrator", MEDIUM),
    FORCED_PASSWORD_RESET("Administrator required a password change", HIGH);

    private final String label;
    private final SecuritySeverity severity;
}
