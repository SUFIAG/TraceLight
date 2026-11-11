package com.sentinel.security.application.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Every security threshold in one place, so an operator can tighten the
 * platform without reading the code, and so a reviewer can see the whole
 * posture at a glance.
 *
 * <p>Defaults are the values a retail bank would recognise: three attempts
 * before a lockout, a twelve-character password, a thirty-minute reset window.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.security")
@Getter
@Setter
public class SecurityProperties {

    /** Failed sign-ins before the account locks. */
    private int maxFailedAttempts = 3;

    /** First lockout length. Each subsequent lockout doubles it, up to the cap. */
    private int lockoutMinutes = 15;

    private int maxLockoutMinutes = 24 * 60;

    /** Failures older than this no longer count towards a lockout. */
    private int failedAttemptWindowMinutes = 60;

    private int minPasswordLength = 12;

    private int maxPasswordLength = 128;

    /** How many previous passwords are remembered and refused. */
    private int passwordHistorySize = 5;

    /** Days before a password must be changed. Zero disables expiry. */
    private int passwordMaxAgeDays = 0;

    /** Sign-in requires a verified email address. */
    private boolean requireEmailVerification = false;

    /**
     * Returns verification and reset codes in the API response.
     *
     * <p>Without a mail or SMS provider wired up there is no other way to
     * complete a verification on a developer machine. It must be off anywhere
     * a real address could be enrolled: it turns "I know your email" into "I
     * control your account".
     */
    private boolean exposeVerificationCodes = true;

    /** Minutes a second factor stays fresh before a sensitive action re-asks. */
    private int stepUpFreshnessMinutes = 15;

    /** A session with no activity for this long is dead even if unexpired. */
    private int sessionIdleMinutes = 60 * 12;

    /** Concurrent signed-in devices per user. Oldest is dropped beyond this. */
    private int maxSessionsPerUser = 10;

    /** Sign-in attempts allowed per address and IP inside the window. */
    private int loginAttemptsPerWindow = 10;

    private int loginWindowMinutes = 5;

    /** Requests per minute per IP across the whole API. */
    private int globalRequestsPerMinute = 600;

    /** Verification or reset messages one user may request per hour. */
    private int verificationRequestsPerHour = 5;
}
