package com.sentinel.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Second factor presented against a pending sign-in challenge. */
public record MfaVerifyRequest(
        @NotNull(message = "Challenge is required") UUID challengeId,
        @NotBlank(message = "Enter the code") String code) {
}
