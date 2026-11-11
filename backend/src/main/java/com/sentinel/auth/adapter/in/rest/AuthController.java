package com.sentinel.auth.adapter.in.rest;

import com.sentinel.auth.application.dto.*;
import com.sentinel.auth.application.service.AuthService;
import com.sentinel.common.config.AuthenticationDetails;
import com.sentinel.common.config.JwtTokenProvider;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.security.application.service.PasswordPolicyService;
import com.sentinel.security.application.service.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordPolicyService passwordPolicyService;
    private final SecurityProperties securityProperties;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Organization used when a request carries no X-Organization-Id header.
     * Configurable because the value must match the organization seeded by
     * migration V1; hardcoding it meant a mismatch could only be found by
     * failing to log in.
     */
    @Value("${sentinel.default-organization-id}")
    private UUID defaultOrganizationId;

    /* ---------- Credential exchange ---------- */

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @RequestBody @Valid RegisterRequest request, HttpServletRequest http) {
        UserResponse response = authService.register(request, extractOrganizationId(http), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.login(request, extractOrganizationId(http), http)));
    }

    /** Completes a sign-in that stopped at the second factor. */
    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyMfa(
            @RequestBody @Valid MfaVerifyRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.verifyMfa(request.challengeId(), request.code(), http)));
    }

    /** Sends a one-time code by text for a sign-in already in progress. */
    @PostMapping("/mfa/sms")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMfaSms(
            @RequestBody Map<String, String> body, HttpServletRequest http) {
        UUID challengeId = UUID.fromString(body.get("challengeId"));
        return ResponseEntity.ok(ApiResponse.success(authService.sendMfaSms(challengeId, http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @RequestBody @Valid TokenRefreshRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.refreshToken(request.getRefreshToken(), http)));
    }

    /**
     * Ends this session and denies the token that made the call, so the token
     * stops working immediately rather than at its expiry.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(
            Authentication auth, HttpServletRequest http) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        UUID sessionId = auth.getDetails() instanceof AuthenticationDetails details
                ? details.sessionId() : null;

        String header = http.getHeader("Authorization");
        String jti = null;
        Instant expiry = null;
        if (header != null && header.startsWith("Bearer ")) {
            var claims = jwtTokenProvider.getClaims(header.substring(7));
            jti = claims.getId();
            expiry = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
        }

        authService.logout(userId, sessionId, jti, expiry, http);
        return ResponseEntity.ok(ApiResponse.success(Map.of("signedOut", true)));
    }

    /* ---------- Password ---------- */

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            Authentication auth, HttpServletRequest http) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        authService.changePassword(userId, request.currentPassword(), request.newPassword(), http);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "changed", true,
                "message", "Password changed. Every other device has been signed out.")));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.forgotPassword(request.email(), extractOrganizationId(http), http)));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request, HttpServletRequest http) {
        authService.resetPassword(request.token(), request.newPassword(), http);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "reset", true,
                "message", "Password reset. Sign in with your new password.")));
    }

    /**
     * The password rules, so the console can state them up front and score a
     * candidate as it is typed. Public: a signed-out person resetting their
     * password needs them too.
     */
    @PostMapping("/password/policy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> passwordPolicy(
            @RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("minLength", securityProperties.getMinPasswordLength());
        policy.put("maxLength", securityProperties.getMaxPasswordLength());
        policy.put("historySize", securityProperties.getPasswordHistorySize());
        policy.put("requirements", List.of(
                "At least " + securityProperties.getMinPasswordLength() + " characters",
                "A capital and a lower-case letter",
                "A number and a symbol",
                "Not a password from a breach list, and not one of your last "
                        + securityProperties.getPasswordHistorySize()));
        if (body != null && body.get("password") != null) {
            policy.put("strength", passwordPolicyService.strength(body.get("password")));
        }
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    /* ---------- Email verification ---------- */

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<UserResponse>> verifyEmail(
            @RequestBody @Valid VerifyCodeRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyEmail(request.code(), http)));
    }

    @PostMapping("/email/resend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resendEmailVerification(
            Authentication auth, HttpServletRequest http) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        var issued = authService.sendEmailVerification(authService.requireUser(userId), http);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", issued != null);
        if (issued != null && securityProperties.isExposeVerificationCodes()) {
            result.put("developmentCode", issued.plaintext());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /* ---------- Reads ---------- */

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> currentUser(Authentication auth) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        UUID orgId = UUID.fromString((String) auth.getCredentials());
        return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser(userId, orgId)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> organizationMembers(Authentication auth) {
        UUID orgId = UUID.fromString((String) auth.getCredentials());
        return ResponseEntity.ok(ApiResponse.success(authService.listOrganizationMembers(orgId)));
    }

    private UUID extractOrganizationId(HttpServletRequest request) {
        String orgId = request.getHeader("X-Organization-Id");
        if (orgId == null || orgId.isBlank()) {
            return defaultOrganizationId;
        }
        return UUID.fromString(orgId);
    }
}
