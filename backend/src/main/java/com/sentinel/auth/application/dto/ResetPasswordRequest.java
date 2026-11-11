package com.sentinel.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Completes a password reset with the token from the message. */
public record ResetPasswordRequest(
        @NotBlank(message = "The reset token is missing") String token,
        @NotBlank(message = "Enter a new password") String newPassword) {
}
