package com.sentinel.auth.application.service;

import com.sentinel.audit.application.service.AuditService;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.auth.adapter.out.persistence.OrganizationRepository;
import com.sentinel.auth.adapter.out.persistence.UserRepository;
import com.sentinel.auth.application.dto.*;
import com.sentinel.auth.domain.model.User;
import com.sentinel.auth.domain.model.UserRole;
import com.sentinel.common.config.JwtTokenProvider;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.security.application.service.*;
import com.sentinel.security.domain.model.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Sign-in, sign-out, and credential changes.
 *
 * <p>The sign-in path is deliberately uniform. A wrong password, an unknown
 * address, and a disabled account all produce the same message and comparable
 * work, because any difference between them is an account-enumeration oracle:
 * an attacker who can tell "no such user" from "wrong password" has a list of
 * your customers before they guess a single credential.
 *
 * <p>What is not uniform is a lockout. Once an account is locked the user is
 * told how long for, because somebody being told "invalid credentials" while
 * locked out will keep trying and keep extending it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    private final UserSecurityService userSecurityService;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;
    private final MfaService mfaService;
    private final VerificationService verificationService;
    private final SecurityEventService securityEvents;
    private final TokenValidationService tokenValidationService;
    private final SecurityProperties properties;

    /* ---------- Registration ---------- */

    @Transactional
    public UserResponse register(RegisterRequest request, UUID organizationId, HttpServletRequest http) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId.toString()));

        passwordPolicyService.validate(request.getPassword(), request.getEmail(),
                List.of(String.valueOf(request.getFirstName()), String.valueOf(request.getLastName())), null);

        if (userRepository.existsByEmailAndOrganizationId(request.getEmail(), organizationId)) {
            // Registration cannot hide that an address is taken — the account
            // has to be rejected — so the response is honest here and the rate
            // limiter is what stops the endpoint being used as a user directory.
            throw new BusinessException("USER_EXISTS", "An account already exists for this email address");
        }

        User saved = userRepository.save(User.builder()
                .organizationId(organizationId)
                .email(request.getEmail().toLowerCase(Locale.ROOT).trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(UserRole.ANALYST)
                .enabled(true)
                .build());

        UserSecurity security = userSecurityService.forUser(saved);
        security.setPasswordChangedAt(Instant.now());
        passwordPolicyService.remember(security, saved.getPasswordHash());
        userSecurityService.save(security);

        sendEmailVerification(saved, http);

        auditService.logAction(organizationId, saved.getId(), AuditAction.USER_CREATED, EntityType.USER);
        log.info("User registered: {} in org {}", saved.getEmail(), organizationId);
        return UserResponse.from(saved, security);
    }

    /* ---------- Sign-in ---------- */

    @Transactional
    public LoginResponse login(LoginRequest request, UUID organizationId, HttpServletRequest http) {
        String email = request.getEmail() == null ? "" : request.getEmail().toLowerCase(Locale.ROOT).trim();
        User user = userRepository.findByEmailAndOrganizationId(email, organizationId).orElse(null);

        if (user == null) {
            // Spend comparable time on an unknown address so response timing
            // does not reveal which addresses exist.
            passwordEncoder.encode(request.getPassword() == null ? "" : request.getPassword());
            securityEvents.record(SecurityEventType.LOGIN_FAILED, organizationId, null, email, http,
                    Map.of("reason", "NO_SUCH_USER"));
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        UserSecurity security = userSecurityService.forUser(user);
        userSecurityService.assertNotLocked(security, email, http);

        if (!user.isEnabled()) {
            securityEvents.record(SecurityEventType.LOGIN_BLOCKED_DISABLED, organizationId,
                    user.getId(), email, http, null);
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw userSecurityService.failureException(
                    userSecurityService.recordFailure(security, email, http));
        }

        userSecurityService.recordSuccess(security);

        if (properties.isRequireEmailVerification() && !security.isEmailVerified()) {
            VerificationService.Issued issued = sendEmailVerification(user, http);
            throw new BusinessException("EMAIL_NOT_VERIFIED",
                    "Verify your email address before signing in. We have sent a new code to "
                            + Secrets.maskEmail(user.getEmail())
                            + (verificationService.codesAreExposed() && issued != null
                            ? " (development code " + issued.plaintext() + ")" : "."));
        }

        if (security.isMfaEnabled()) {
            VerificationService.Issued challenge = verificationService.issue(
                    user, AuthTokenType.MFA_CHALLENGE, user.getEmail(), false, http);
            securityEvents.record(SecurityEventType.MFA_CHALLENGED, organizationId,
                    user.getId(), email, http, null);

            List<String> methods = new ArrayList<>(List.of(MfaMethod.TOTP.name()));
            if (security.isPhoneVerified()) methods.add(MfaMethod.SMS.name());
            if (security.getBackupCodes() != null && !security.getBackupCodes().isEmpty()) {
                methods.add(MfaMethod.BACKUP_CODE.name());
            }

            return LoginResponse.builder()
                    .mfaRequired(true)
                    .challengeId(challenge.id())
                    .mfaMethods(methods)
                    .tokenType("Bearer")
                    .emailVerified(security.isEmailVerified())
                    .build();
        }

        return issueSession(user, security, false, http);
    }

    /** Completes a sign-in that was waiting on a second factor. */
    @Transactional
    public LoginResponse verifyMfa(UUID challengeId, String code, HttpServletRequest http) {
        AuthToken challenge = verificationService.findUsable(challengeId, AuthTokenType.MFA_CHALLENGE);
        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new BusinessException("CHALLENGE_EXPIRED",
                        "This sign-in has expired. Start again."));

        UserSecurity security = userSecurityService.forUser(user);
        userSecurityService.assertNotLocked(security, user.getEmail(), http);

        try {
            mfaService.verify(user, code, http);
        } catch (BusinessException e) {
            // A wrong second factor counts towards the same lockout as a wrong
            // password. Otherwise MFA codes could be guessed without limit.
            UserSecurityService.FailureOutcome outcome =
                    userSecurityService.recordFailure(security, user.getEmail(), http);
            if (outcome.locked()) throw userSecurityService.failureException(outcome);
            throw e;
        }

        verificationService.markConsumed(challenge);
        return issueSession(user, security, true, http);
    }

    /** Sends a one-time code to the phone on file, mid sign-in. */
    @Transactional
    public Map<String, Object> sendMfaSms(UUID challengeId, HttpServletRequest http) {
        AuthToken challenge = verificationService.findUsable(challengeId, AuthTokenType.MFA_CHALLENGE);
        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new BusinessException("CHALLENGE_EXPIRED",
                        "This sign-in has expired. Start again."));

        String developmentCode = mfaService.sendSmsCode(user, http);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentTo", Secrets.maskPhone(
                userSecurityService.forUser(user).getPhoneNumber()));
        if (developmentCode != null) result.put("developmentCode", developmentCode);
        return result;
    }

    /* ---------- Session lifecycle ---------- */

    @Transactional
    public LoginResponse refreshToken(String refreshToken, HttpServletRequest http) {
        SessionService.Established rotated = sessionService.rotate(refreshToken, http);
        User user = userRepository.findById(rotated.session().getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User",
                        rotated.session().getUserId().toString()));

        if (!user.isEnabled()) {
            sessionService.revokeAll(user.getId(), "ACCOUNT_DISABLED");
            throw new BusinessException("USER_DISABLED", "This account has been disabled.");
        }

        UserSecurity security = userSecurityService.forUser(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getOrganizationId(), user.getRole().name(),
                rotated.session().getId(), security.getTokenVersion(),
                rotated.session().getMfaAt() != null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rotated.refreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .user(UserResponse.from(user, security))
                .emailVerified(security.isEmailVerified())
                .mustChangePassword(security.isMustChangePassword())
                .build();
    }

    /**
     * Ends the current session and denies the access token that was used.
     *
     * <p>Clearing the token client-side is not sign-out: the token stays valid
     * until it expires, and anything that copied it still works.
     */
    @Transactional
    public void logout(UUID userId, UUID sessionId, String jti, Instant tokenExpiry,
                       HttpServletRequest http) {
        if (jti != null) {
            sessionService.denyAccessToken(jti, userId, tokenExpiry, "LOGOUT");
        }
        if (sessionId != null) {
            sessionService.revokeById(userId, sessionId, http);
        }
    }

    /* ---------- Credentials ---------- */

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword,
                               HttpServletRequest http) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        UserSecurity security = userSecurityService.forUser(user);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            UserSecurityService.FailureOutcome outcome =
                    userSecurityService.recordFailure(security, user.getEmail(), http);
            if (outcome.locked()) throw userSecurityService.failureException(outcome);
            throw new BusinessException("INVALID_CREDENTIALS", "Your current password is not correct");
        }

        passwordPolicyService.validate(newPassword, user.getEmail(),
                List.of(String.valueOf(user.getFirstName()), String.valueOf(user.getLastName())), security);

        applyNewPassword(user, security, newPassword);
        sessionService.revokeAll(userId, "PASSWORD_CHANGED");

        securityEvents.record(SecurityEventType.PASSWORD_CHANGED, user.getOrganizationId(),
                userId, user.getEmail(), http, null);
    }

    /**
     * Starts a password reset.
     *
     * <p>Always reports success. Saying "no account with that address" turns
     * the endpoint into a customer list, and a bank's customer list is itself
     * sensitive.
     */
    @Transactional
    public Map<String, Object> forgotPassword(String email, UUID organizationId, HttpServletRequest http) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",
                "If an account exists for that address, a reset link is on its way.");

        String normalised = email == null ? "" : email.toLowerCase(Locale.ROOT).trim();
        userRepository.findByEmailAndOrganizationId(normalised, organizationId).ifPresent(user -> {
            VerificationService.Issued issued = verificationService.issue(
                    user, AuthTokenType.PASSWORD_RESET, user.getEmail(), false, http);
            verificationService.sendEmailCode(user.getEmail(), issued.plaintext(), "password reset",
                    AuthTokenType.PASSWORD_RESET.getLifetimeMinutes());
            securityEvents.record(SecurityEventType.PASSWORD_RESET_REQUESTED, organizationId,
                    user.getId(), user.getEmail(), http, null);
            if (verificationService.codesAreExposed()) {
                response.put("developmentToken", issued.plaintext());
            }
        });
        return response;
    }

    @Transactional
    public void resetPassword(String token, String newPassword, HttpServletRequest http) {
        AuthToken authToken = verificationService.consume(token, AuthTokenType.PASSWORD_RESET);
        User user = userRepository.findById(authToken.getUserId())
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "That reset link is not valid."));
        UserSecurity security = userSecurityService.forUser(user);

        passwordPolicyService.validate(newPassword, user.getEmail(),
                List.of(String.valueOf(user.getFirstName()), String.valueOf(user.getLastName())), security);

        applyNewPassword(user, security, newPassword);

        // A reset is the remedy for a lockout, so clear it, and end every
        // session: if the reset was prompted by a compromise, the attacker's
        // sessions must not survive it.
        security.setLockedUntil(null);
        security.setFailedAttempts(0);
        userSecurityService.save(security);
        sessionService.revokeAll(user.getId(), "PASSWORD_RESET");

        securityEvents.record(SecurityEventType.PASSWORD_RESET_COMPLETED, user.getOrganizationId(),
                user.getId(), user.getEmail(), http, null);
    }

    /* ---------- Email verification ---------- */

    @Transactional
    public VerificationService.Issued sendEmailVerification(User user, HttpServletRequest http) {
        try {
            VerificationService.Issued issued = verificationService.issue(
                    user, AuthTokenType.EMAIL_VERIFICATION, user.getEmail(), true, http);
            verificationService.sendEmailCode(user.getEmail(), issued.plaintext(), "verification",
                    AuthTokenType.EMAIL_VERIFICATION.getLifetimeMinutes());
            securityEvents.record(SecurityEventType.EMAIL_VERIFICATION_SENT, user.getOrganizationId(),
                    user.getId(), user.getEmail(), http, null);
            return issued;
        } catch (BusinessException e) {
            // Hitting the resend budget must not fail the registration that
            // triggered it; the user can ask again later.
            log.warn("Could not send a verification code to {}: {}", user.getEmail(), e.getMessage());
            return null;
        }
    }

    @Transactional
    public UserResponse verifyEmail(String code, HttpServletRequest http) {
        AuthToken token = verificationService.consume(code, AuthTokenType.EMAIL_VERIFICATION);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException("INVALID_CODE", "That code is not valid."));

        UserSecurity security = userSecurityService.forUser(user);
        security.setEmailVerified(true);
        security.setEmailVerifiedAt(Instant.now());
        userSecurityService.save(security);

        securityEvents.record(SecurityEventType.EMAIL_VERIFIED, user.getOrganizationId(),
                user.getId(), user.getEmail(), http, null);
        return UserResponse.from(user, security);
    }

    /* ---------- Reads ---------- */

    @Transactional(readOnly = true)
    public List<UserResponse> listOrganizationMembers(UUID organizationId) {
        return userRepository.findByOrganizationIdOrderByFirstNameAsc(organizationId).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse getCurrentUser(UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return UserResponse.from(user, userSecurityService.forUser(user));
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    /** Confirms a password without signing in, for a sensitive confirmation dialog. */
    @Transactional(readOnly = true)
    public boolean passwordMatches(UUID userId, String password) {
        return userRepository.findById(userId)
                .map(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElse(false);
    }

    /* ---------- Internals ---------- */

    private LoginResponse issueSession(User user, UserSecurity security, boolean mfaSatisfied,
                                       HttpServletRequest http) {
        user.setLastLogin(Instant.now());
        userRepository.save(user);

        SessionService.Established established =
                sessionService.create(user.getId(), user.getOrganizationId(), mfaSatisfied, http);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getOrganizationId(), user.getRole().name(),
                established.session().getId(), security.getTokenVersion(), mfaSatisfied);

        auditService.logAction(user.getOrganizationId(), user.getId(),
                AuditAction.USER_LOGIN, EntityType.USER);
        securityEvents.record(SecurityEventType.LOGIN_SUCCEEDED, user.getOrganizationId(),
                user.getId(), user.getEmail(), http, Map.of("mfa", mfaSatisfied));

        boolean mustChange = security.isMustChangePassword() || userSecurityService.passwordExpired(security);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(established.refreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .user(UserResponse.from(user, security))
                .emailVerified(security.isEmailVerified())
                .mustChangePassword(mustChange)
                .build();
    }

    private void applyNewPassword(User user, UserSecurity security, String newPassword) {
        String hash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hash);
        userRepository.save(user);

        passwordPolicyService.remember(security, hash);
        security.setPasswordChangedAt(Instant.now());
        security.setMustChangePassword(false);
        security.setTokenVersion(security.getTokenVersion() + 1);
        userSecurityService.save(security);
        tokenValidationService.invalidate(user.getId());
    }
}
