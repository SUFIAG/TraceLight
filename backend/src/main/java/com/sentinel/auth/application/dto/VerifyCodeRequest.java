package com.sentinel.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

/** A short code typed by the user: email verification or phone confirmation. */
public record VerifyCodeRequest(@NotBlank(message = "Enter the code") String code) {
}
