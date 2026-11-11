package com.sentinel.security.adapter.in.rest;

import com.sentinel.auth.application.service.AuthService;
import com.sentinel.auth.domain.model.User;
import com.sentinel.common.config.AuthenticationDetails;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.adapter.out.persistence.SecurityEventRepository;
import com.sentinel.security.application.service.*;
import com.sentinel.security.domain.model.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Everything a signed-in person can do about their own security: enrol a
 * second factor, confirm a phone, see where they are signed in, and read their
 * own security history.
 *
 * <p>Each sensitive change asks for the password again. A session left open on
 * an unattended laptop should not be enough to remove a factor.
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final AuthService authService;
    private final UserSecurityService userSecurityService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final VerificationService verificationService;
    private final SecurityEventService securityEvents;
    private final SecurityEventRepository securityEventRepository;
    private final SecurityProperties properties;

    /* ---------- Posture ---------- */

    /** One call that tells the settings screen everything it must render. */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(Authentication auth) {
        User user = authService.requireUser(userId(auth));
        UserSecurity security = userSecurityService.forUser(user);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("mfaEnabled", security.isMfaEnabled());
        overview.put("mfaMethod", security.getMfaMethod());
        overview.put("mfaEnrolledAt", security.getMfaEnrolledAt());
        overview.put("backupCodesRemaining",
                security.getBackupCodes() == null ? 0 : security.getBackupCodes().size());
        overview.put("emailVerified", security.isEmailVerified());
        overview.put("email", Secrets.maskEmail(user.getEmail()));
        overview.put("phoneVerified", security.isPhoneVerified());
        overview.put("phoneNumber", Secrets.maskPhone(security.getPhoneNumber()));
        overview.put("passwordChangedAt", security.getPasswordChangedAt());
        overview.put("passwordAgeDays", security.getPasswordChangedAt() == null ? null
                : Duration.between(security.getPasswordChangedAt(), Instant.now()).toDays());
        overview.put("mustChangePassword", security.isMustChangePassword());
        overview.put("activeSessions", sessionService.listForUser(user.getId()).size());
        overview.put("stepUpFreshnessMinutes", properties.getStepUpFreshnessMinutes());

        // A plain score the user can act on, rather than a badge that only says
        // "at risk" without saying what to do about it.
        List<String> advice = new ArrayList<>();
        int score = 40;
        if (security.isMfaEnabled()) score += 35; else advice.add("Turn on multi-factor authentication");
        if (security.isEmailVerified()) score += 15; else advice.add("Verify your email address");
        if (security.isPhoneVerified()) score += 10; else advice.add("Add a phone number for recovery");
        overview.put("score", Math.min(100, score));
        overview.put("advice", advice);

        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    /* ---------- Multi-factor authentication ---------- */

    @PostMapping("/mfa/enrol")
    public ResponseEntity<ApiResponse<Map<String, Object>>> beginEnrolment(Authentication auth) {
        User user = authService.requireUser(userId(auth));
        MfaService.Enrolment enrolment = mfaService.beginEnrolment(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("secret", enrolment.secret());
        response.put("provisioningUri", enrolment.provisioningUri());
        response.put("issuer", "Tracelight");
        response.put("account", user.getEmail());
        response.put("next", "Scan the code in your authenticator app, then confirm the six digits it shows.");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/mfa/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        User user = authService.requireUser(userId(auth));
        MfaService.Activation activation = mfaService.activate(user, body.get("code"), http);

        // The current device just proved a factor, so mark it: otherwise the
        // user is asked to step up again seconds after enrolling.
        sessionIdOf(auth).ifPresent(sessionService::markMfaSatisfied);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "enabled", true,
                "backupCodes", activation.backupCodes(),
                "warning", "Save these recovery codes now. They are shown once.")));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disable(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        User user = authService.requireUser(userId(auth));
        requirePassword(user.getId(), body.get("password"));
        mfaService.verify(user, body.get("code"), http);
        mfaService.disable(user, http);
        return ResponseEntity.ok(ApiResponse.success(Map.of("enabled", false)));
    }

    @PostMapping("/mfa/backup-codes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> regenerateBackupCodes(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        User user = authService.requireUser(userId(auth));
        requirePassword(user.getId(), body.get("password"));
        List<String> codes = mfaService.regenerateBackupCodes(user, http);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "backupCodes", codes,
                "warning", "Your previous recovery codes no longer work.")));
    }

    /**
     * Re-confirms identity on the current device so a sensitive action can
     * proceed.
     *
     * <p>Accepts a current authenticator code, or the account password for
     * users without a second factor. Without this endpoint a step-up refusal
     * would be a dead end: the console could tell the user what was wrong but
     * give them no way to fix it.
     */
    @PostMapping("/step-up")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stepUp(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        User user = authService.requireUser(userId(auth));
        UserSecurity security = userSecurityService.forUser(user);

        if (security.isMfaEnabled()) {
            mfaService.verify(user, body.get("code"), http);
        } else {
            requirePassword(user.getId(), body.get("password"));
        }

        UUID sessionId = sessionIdOf(auth).orElseThrow(() -> new BusinessException(
                "NO_SESSION", "This token is not tied to a device session. Sign in again."));
        sessionService.markMfaSatisfied(sessionId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "confirmed", true,
                "validForMinutes", properties.getStepUpFreshnessMinutes())));
    }

    /* ---------- Phone ---------- */

    @PostMapping("/phone/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendPhoneCode(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        User user = authService.requireUser(userId(auth));
        String phoneNumber = normalisePhone(body.get("phoneNumber"));

        UserSecurity security = userSecurityService.forUser(user);
        security.setPhoneNumber(phoneNumber);
        security.setPhoneVerified(false);
        userSecurityService.save(security);

        VerificationService.Issued issued = verificationService.issue(
                user, AuthTokenType.PHONE_OTP, phoneNumber, true, http);
        verificationService.sendSmsCode(phoneNumber, issued.plaintext(),
                AuthTokenType.PHONE_OTP.getLifetimeMinutes());
        securityEvents.record(SecurityEventType.PHONE_VERIFICATION_SENT, user.getOrganizationId(),
                user.getId(), user.getEmail(), http, Map.of("destination", Secrets.maskPhone(phoneNumber)));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sentTo", Secrets.maskPhone(phoneNumber));
        response.put("expiresInMinutes", AuthTokenType.PHONE_OTP.getLifetimeMinutes());
        if (verificationService.codesAreExposed()) response.put("developmentCode", issued.plaintext());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyPhone(
            @RequestBody Map<String, String> body, Authentication auth, HttpServletRequest http) {
        User user = authService.requireUser(userId(auth));
        verificationService.consume(body.get("code"), AuthTokenType.PHONE_OTP);

        UserSecurity security = userSecurityService.forUser(user);
        security.setPhoneVerified(true);
        security.setPhoneVerifiedAt(Instant.now());
        userSecurityService.save(security);

        securityEvents.record(SecurityEventType.PHONE_VERIFIED, user.getOrganizationId(),
                user.getId(), user.getEmail(), http, null);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "verified", true,
                "phoneNumber", Secrets.maskPhone(security.getPhoneNumber()))));
    }

    @DeleteMapping("/phone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removePhone(Authentication auth) {
        User user = authService.requireUser(userId(auth));
        UserSecurity security = userSecurityService.forUser(user);
        security.setPhoneNumber(null);
        security.setPhoneVerified(false);
        security.setPhoneVerifiedAt(null);
        userSecurityService.save(security);
        return ResponseEntity.ok(ApiResponse.success(Map.of("removed", true)));
    }

    /* ---------- Sessions ---------- */

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> sessions(Authentication auth) {
        UUID currentSession = sessionIdOf(auth).orElse(null);
        List<Map<String, Object>> sessions = sessionService.listForUser(userId(auth)).stream()
                .map(session -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", session.getId());
                    row.put("device", session.getDeviceLabel());
                    row.put("ipAddress", session.getIpAddress());
                    row.put("createdAt", session.getCreatedAt());
                    row.put("lastSeenAt", session.getLastSeenAt());
                    row.put("expiresAt", session.getExpiresAt());
                    row.put("mfaAt", session.getMfaAt());
                    row.put("current", session.getId().equals(currentSession));
                    return row;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeSession(
            @PathVariable UUID sessionId, Authentication auth, HttpServletRequest http) {
        sessionService.revokeById(userId(auth), sessionId, http);
        return ResponseEntity.ok(ApiResponse.success(Map.of("revoked", true)));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeOtherSessions(
            Authentication auth, HttpServletRequest http) {
        int revoked = sessionService.revokeOthers(userId(auth), sessionIdOf(auth).orElse(null), http);
        return ResponseEntity.ok(ApiResponse.success(Map.of("sessionsEnded", revoked)));
    }

    /* ---------- History ---------- */

    /** The signed-in user's own security history. Always visible to them. */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<Page<SecurityEvent>>> myEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(securityEventRepository
                .findByUserIdOrderByCreatedAtDesc(userId(auth), PageRequest.of(page, Math.min(size, 100)))));
    }

    /** The whole organization's security history. Administrators and reviewers only. */
    @GetMapping("/events/organization")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','AUDITOR')")
    public ResponseEntity<ApiResponse<Page<SecurityEvent>>> organizationEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(securityEventRepository
                .findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, Math.min(size, 200)))));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','AUDITOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID organizationId = orgId(auth);
        Instant since = Instant.now().minus(Duration.ofDays(7));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("eventsLast7Days",
                securityEventRepository.countByOrganizationIdAndCreatedAtAfter(organizationId, since));
        stats.put("failedSignIns", securityEventRepository.countByOrganizationIdAndEventTypeAndCreatedAtAfter(
                organizationId, SecurityEventType.LOGIN_FAILED, since));
        stats.put("lockouts", securityEventRepository.countByOrganizationIdAndEventTypeAndCreatedAtAfter(
                organizationId, SecurityEventType.ACCOUNT_LOCKED, since));
        stats.put("mfaFailures", securityEventRepository.countByOrganizationIdAndEventTypeAndCreatedAtAfter(
                organizationId, SecurityEventType.MFA_FAILED, since));
        stats.put("tokenReplays", securityEventRepository.countByOrganizationIdAndEventTypeAndCreatedAtAfter(
                organizationId, SecurityEventType.REFRESH_TOKEN_REUSED, since));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /* ---------- Internals ---------- */

    private void requirePassword(UUID userId, String password) {
        if (password == null || !authService.passwordMatches(userId, password)) {
            throw new BusinessException("INVALID_CREDENTIALS",
                    "Enter your current password to make this change");
        }
    }

    /**
     * Keeps digits and a leading plus. Anything else in a phone number is
     * formatting the SMS gateway will reject anyway.
     */
    private String normalisePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_PHONE", "Enter a phone number");
        }
        String cleaned = raw.trim().replaceAll("(?!^\\+)[^0-9]", "");
        if (cleaned.replaceAll("\\D", "").length() < 7) {
            throw new BusinessException("INVALID_PHONE", "That does not look like a phone number");
        }
        return cleaned;
    }

    private UUID userId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private Optional<UUID> sessionIdOf(Authentication auth) {
        return auth.getDetails() instanceof AuthenticationDetails details
                ? Optional.ofNullable(details.sessionId()) : Optional.empty();
    }
}
