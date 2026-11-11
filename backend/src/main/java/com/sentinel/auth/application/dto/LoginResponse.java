package com.sentinel.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * The answer to a sign-in attempt, which is not always a token.
 *
 * <p>When the account has a second factor the password alone buys only a
 * challenge: {@code mfaRequired} is true, {@code challengeId} identifies the
 * half-finished sign-in, and no token is issued until the code is checked.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserResponse user;

    /** True when the password was accepted but a second factor is still needed. */
    @Builder.Default
    private boolean mfaRequired = false;

    private UUID challengeId;

    /** The factors this account can satisfy, in the order the console should offer them. */
    private List<String> mfaMethods;

    /** Set when the password is correct but expired or administratively retired. */
    @Builder.Default
    private boolean mustChangePassword = false;

    /** Lets the console show the "verify your email" prompt without another call. */
    @Builder.Default
    private boolean emailVerified = true;

    /**
     * Development only: the code that would have been sent, echoed back when
     * {@code sentinel.security.expose-verification-codes} is on.
     */
    private String developmentCode;
}
