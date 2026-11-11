package com.sentinel.security.application.service;

import com.sentinel.audit.application.service.AuditService;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.auth.adapter.out.persistence.UserRepository;
import com.sentinel.auth.application.dto.UserResponse;
import com.sentinel.auth.domain.model.User;
import com.sentinel.auth.domain.model.UserRole;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.security.adapter.out.persistence.UserSecurityRepository;
import com.sentinel.security.domain.model.SecurityEventType;
import com.sentinel.security.domain.model.UserSecurity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Administrator control over the people in an organization.
 *
 * <p>Two rules are enforced here rather than left to the caller, because both
 * failures are unrecoverable from inside the product: an administrator cannot
 * strip their own administrator role, and the last administrator cannot be
 * demoted or disabled. Either would leave an organization with nobody able to
 * approve a model or issue a key, and no way back without database access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    private final UserRepository userRepository;
    private final UserSecurityRepository userSecurityRepository;
    private final UserSecurityService userSecurityService;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;
    private final SecurityEventService securityEvents;
    private final TokenValidationService tokenValidationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** The team list the settings screen shows, with each member's posture. */
    @Transactional(readOnly = true)
    public List<UserResponse> listMembers(UUID organizationId) {
        List<User> users = userRepository.findByOrganizationIdOrderByFirstNameAsc(organizationId);
        Map<UUID, UserSecurity> postures = userSecurityRepository
                .findByUserIdIn(users.stream().map(User::getId).toList()).stream()
                .collect(HashMap::new, (map, security) -> map.put(security.getUserId(), security), HashMap::putAll);

        return users.stream()
                .map(user -> UserResponse.from(user, postures.get(user.getId())))
                .toList();
    }

    /**
     * Creates an account with a one-time password the administrator passes on.
     *
     * <p>The password must be changed at first sign-in, so the value the
     * administrator saw stops working the moment the user arrives.
     */
    @Transactional
    public Map<String, Object> invite(UUID organizationId, String email, String firstName, String lastName,
                                      UserRole role, UUID actorId, HttpServletRequest http) {
        String normalised = email == null ? "" : email.toLowerCase(Locale.ROOT).trim();
        if (normalised.isBlank() || !normalised.contains("@")) {
            throw new BusinessException("INVALID_EMAIL", "Enter a valid email address");
        }
        if (userRepository.existsByEmailAndOrganizationId(normalised, organizationId)) {
            throw new BusinessException("USER_EXISTS", "An account already exists for this address");
        }

        // Generated rather than chosen: an administrator-picked first password
        // is reused across the team more often than not.
        String temporaryPassword = "Tl-" + Secrets.token(5) + "-"
                + Secrets.numericCode(2) + "!";

        User user = userRepository.save(User.builder()
                .organizationId(organizationId)
                .email(normalised)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .firstName(firstName)
                .lastName(lastName)
                .role(role == null ? UserRole.ANALYST : role)
                .enabled(true)
                .build());

        UserSecurity security = userSecurityService.forUser(user);
        security.setMustChangePassword(true);
        security.setPasswordChangedAt(Instant.now());
        passwordPolicyService.remember(security, user.getPasswordHash());
        userSecurityRepository.save(security);

        auditService.logAction(organizationId, actorId, AuditAction.USER_CREATED, EntityType.USER);
        securityEvents.record(SecurityEventType.USER_INVITED, organizationId, user.getId(),
                normalised, http, Map.of("role", user.getRole().name(), "invitedBy", String.valueOf(actorId)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", UserResponse.from(user, security));
        result.put("temporaryPassword", temporaryPassword);
        result.put("note", "Share this once. The user must change it at first sign-in.");
        return result;
    }

    @Transactional
    public UserResponse changeRole(UUID organizationId, UUID targetUserId, UserRole role,
                                   UUID actorId, HttpServletRequest http) {
        User user = require(organizationId, targetUserId);
        UserRole previous = user.getRole();
        if (previous == role) return UserResponse.from(user, userSecurityService.forUser(user));

        if (previous == UserRole.ADMIN) {
            assertNotSelf(actorId, targetUserId, "change your own role");
            assertNotLastAdmin(organizationId, targetUserId);
        }

        user.setRole(role);
        userRepository.save(user);

        // The old role is baked into every token already issued. Bump the
        // version so the change takes effect on the next request, not in an
        // hour when the token happens to expire.
        userSecurityService.bumpTokenVersion(targetUserId);
        tokenValidationService.invalidate(targetUserId);

        securityEvents.record(SecurityEventType.ROLE_CHANGED, organizationId, targetUserId,
                user.getEmail(), http, Map.of("from", previous.name(), "to", role.name(),
                        "changedBy", String.valueOf(actorId)));
        auditService.logAction(organizationId, actorId, AuditAction.USER_UPDATED, EntityType.USER);
        return UserResponse.from(user, userSecurityService.forUser(user));
    }

    @Transactional
    public UserResponse setEnabled(UUID organizationId, UUID targetUserId, boolean enabled,
                                   UUID actorId, HttpServletRequest http) {
        User user = require(organizationId, targetUserId);
        if (!enabled) {
            assertNotSelf(actorId, targetUserId, "disable your own account");
            if (user.getRole() == UserRole.ADMIN) assertNotLastAdmin(organizationId, targetUserId);
        }

        user.setEnabled(enabled);
        userRepository.save(user);

        if (!enabled) {
            // A disabled account with a live session is not disabled.
            sessionService.revokeAll(targetUserId, "ACCOUNT_DISABLED");
            userSecurityService.bumpTokenVersion(targetUserId);
            tokenValidationService.invalidate(targetUserId);
        }

        securityEvents.record(enabled ? SecurityEventType.USER_ENABLED : SecurityEventType.USER_DISABLED,
                organizationId, targetUserId, user.getEmail(), http,
                Map.of("changedBy", String.valueOf(actorId)));
        return UserResponse.from(user, userSecurityService.forUser(user));
    }

    @Transactional
    public UserResponse unlock(UUID organizationId, UUID targetUserId, UUID actorId, HttpServletRequest http) {
        User user = require(organizationId, targetUserId);
        UserSecurity security = userSecurityService.unlock(targetUserId, actorId, http);
        return UserResponse.from(user, security);
    }

    /** Forces a password change at next sign-in and ends every current session. */
    @Transactional
    public UserResponse forcePasswordReset(UUID organizationId, UUID targetUserId, UUID actorId,
                                           HttpServletRequest http) {
        User user = require(organizationId, targetUserId);
        UserSecurity security = userSecurityService.forUser(user);
        security.setMustChangePassword(true);
        security.setTokenVersion(security.getTokenVersion() + 1);
        userSecurityRepository.save(security);

        sessionService.revokeAll(targetUserId, "ADMIN_FORCED_RESET");
        tokenValidationService.invalidate(targetUserId);

        securityEvents.record(SecurityEventType.FORCED_PASSWORD_RESET, organizationId, targetUserId,
                user.getEmail(), http, Map.of("requestedBy", String.valueOf(actorId)));
        return UserResponse.from(user, security);
    }

    /** Signs a user out of every device, without otherwise touching the account. */
    @Transactional
    public int revokeSessions(UUID organizationId, UUID targetUserId, UUID actorId, HttpServletRequest http) {
        require(organizationId, targetUserId);
        int revoked = sessionService.revokeAll(targetUserId, "ADMIN_REVOKED");
        userSecurityService.bumpTokenVersion(targetUserId);
        tokenValidationService.invalidate(targetUserId);
        securityEvents.record(SecurityEventType.ALL_SESSIONS_REVOKED, organizationId, targetUserId,
                null, http, Map.of("revokedBy", String.valueOf(actorId), "sessions", revoked));
        return revoked;
    }

    /**
     * Turns off a user's second factor.
     *
     * <p>The lost-phone case. Dangerous by nature — it is exactly what a
     * social-engineering caller asks for — so it is restricted to
     * administrators, logged at high severity, and forces a password change so
     * the account cannot simply be walked into.
     */
    @Transactional
    public UserResponse resetMfa(UUID organizationId, UUID targetUserId, UUID actorId, HttpServletRequest http) {
        User user = require(organizationId, targetUserId);
        UserSecurity security = userSecurityService.forUser(user);
        security.setMfaEnabled(false);
        security.setMfaSecret(null);
        security.setMfaMethod(null);
        security.setBackupCodes(new ArrayList<>());
        security.setMustChangePassword(true);
        security.setTokenVersion(security.getTokenVersion() + 1);
        userSecurityRepository.save(security);

        sessionService.revokeAll(targetUserId, "ADMIN_RESET_MFA");
        tokenValidationService.invalidate(targetUserId);

        securityEvents.record(SecurityEventType.MFA_DISABLED, organizationId, targetUserId,
                user.getEmail(), http, Map.of("resetBy", String.valueOf(actorId), "administrative", true));
        return UserResponse.from(user, security);
    }

    /* ---------- Guards ---------- */

    private User require(UUID organizationId, UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private void assertNotSelf(UUID actorId, UUID targetId, String action) {
        if (Objects.equals(actorId, targetId)) {
            throw new BusinessException("SELF_ACTION_REFUSED",
                    "You cannot " + action + ". Ask another administrator.");
        }
    }

    private void assertNotLastAdmin(UUID organizationId, UUID targetUserId) {
        long remaining = userRepository.findByOrganizationIdOrderByFirstNameAsc(organizationId).stream()
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .filter(user -> !user.getId().equals(targetUserId))
                .count();
        if (remaining == 0) {
            throw new BusinessException("LAST_ADMIN",
                    "This is the only administrator. Promote someone else first.");
        }
    }
}
