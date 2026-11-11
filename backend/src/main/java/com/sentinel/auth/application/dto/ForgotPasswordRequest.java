package com.sentinel.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Opens a password reset. The response never reveals whether the address exists. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Enter your email address")
        @Email(message = "Enter a valid email address") String email) {
}
