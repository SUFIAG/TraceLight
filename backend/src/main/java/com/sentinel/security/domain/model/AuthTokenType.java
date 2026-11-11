package com.sentinel.security.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The short-lived secrets the platform issues outside the JWT flow.
 *
 * <p>Lifetimes are deliberately different: a link somebody clicks from an
 * inbox can last an hour, a six-digit code read off a phone must not.
 */
@Getter
@RequiredArgsConstructor
public enum AuthTokenType {

    EMAIL_VERIFICATION("Email verification", 24 * 60, 10),
    PASSWORD_RESET("Password reset", 30, 5),
    PHONE_OTP("Phone verification code", 10, 5),
    /** Handle for a sign-in that passed the password but not yet the second factor. */
    MFA_CHALLENGE("Sign-in challenge", 5, 5);

    private final String label;
    private final int lifetimeMinutes;
    private final int maxAttempts;
}
