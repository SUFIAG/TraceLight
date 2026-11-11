package com.sentinel.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Password change by a signed-in user, who must prove the current one. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank(message = "Enter a new password") String newPassword) {
}
