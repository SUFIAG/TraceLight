package com.sentinel.security.adapter.in.rest;

import com.sentinel.auth.application.dto.UserResponse;
import com.sentinel.auth.domain.model.UserRole;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.application.service.StepUpRequired;
import com.sentinel.security.application.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrator-facing user management.
 *
 * <p>Every write here changes who can do what, so each one is behind the
 * administrator role, asks for a recent second factor, and lands in the
 * security log with the actor recorded.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> members(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.listMembers(orgId(auth))));
    }

    /** The roles an administrator may assign, with what each one unlocks. */
    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> roles() {
        return ResponseEntity.ok(ApiResponse.success(Arrays.stream(UserRole.values())
                .map(role -> Map.of("value", role.name(), "description", role.getDescription()))
                .toList()));
    }

    @PostMapping
    @StepUpRequired
    public ResponseEntity<ApiResponse<Map<String, Object>>> invite(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.invite(
                orgId(auth),
                body.get("email"),
                body.get("firstName"),
                body.get("lastName"),
                parseRole(body.get("role")),
                userId(auth),
                http)));
    }

    @PatchMapping("/{userId}/role")
    @StepUpRequired
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable UUID userId, @RequestBody Map<String, String> body,
            Authentication auth, HttpServletRequest http) {
        UserRole role = parseRole(body.get("role"));
        if (role == null) throw new BusinessException("INVALID_ROLE", "Choose a role");
        return ResponseEntity.ok(ApiResponse.success(userAdminService.changeRole(
                orgId(auth), userId, role, userId(auth), http)));
    }

    @PatchMapping("/{userId}/status")
    @StepUpRequired
    public ResponseEntity<ApiResponse<UserResponse>> setEnabled(
            @PathVariable UUID userId, @RequestBody Map<String, Boolean> body,
            Authentication auth, HttpServletRequest http) {
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));
        return ResponseEntity.ok(ApiResponse.success(userAdminService.setEnabled(
                orgId(auth), userId, enabled, userId(auth), http)));
    }

    @PostMapping("/{userId}/unlock")
    public ResponseEntity<ApiResponse<UserResponse>> unlock(
            @PathVariable UUID userId, Authentication auth, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.unlock(orgId(auth), userId, userId(auth), http)));
    }

    @PostMapping("/{userId}/force-password-reset")
    @StepUpRequired
    public ResponseEntity<ApiResponse<UserResponse>> forcePasswordReset(
            @PathVariable UUID userId, Authentication auth, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.forcePasswordReset(orgId(auth), userId, userId(auth), http)));
    }

    @PostMapping("/{userId}/revoke-sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeSessions(
            @PathVariable UUID userId, Authentication auth, HttpServletRequest http) {
        int revoked = userAdminService.revokeSessions(orgId(auth), userId, userId(auth), http);
        return ResponseEntity.ok(ApiResponse.success(Map.of("sessionsEnded", revoked)));
    }

    @PostMapping("/{userId}/reset-mfa")
    @StepUpRequired
    public ResponseEntity<ApiResponse<UserResponse>> resetMfa(
            @PathVariable UUID userId, Authentication auth, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.resetMfa(orgId(auth), userId, userId(auth), http)));
    }

    private UserRole parseRole(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ROLE", "'" + value + "' is not a role");
        }
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID userId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
