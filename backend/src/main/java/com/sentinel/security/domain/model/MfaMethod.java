package com.sentinel.security.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How a second factor is presented to the user. */
@Getter
@RequiredArgsConstructor
public enum MfaMethod {

    /** Authenticator app, RFC 6238. Works offline and cannot be SIM-swapped. */
    TOTP("Authenticator app"),

    /** One-time code to a verified phone. Weaker, offered for operators without an app. */
    SMS("Text message"),

    /** Single-use recovery code. Always available once MFA is on. */
    BACKUP_CODE("Recovery code");

    private final String label;
}
