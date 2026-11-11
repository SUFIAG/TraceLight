package com.sentinel.security.application.service;

import com.sentinel.auth.domain.model.User;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.domain.model.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enrolment and checking of the second factor.
 *
 * <p>Enrolment is two steps on purpose. The first hands out a secret and the
 * provisioning URI; the second only switches MFA on once the user has proved
 * the authenticator actually produces matching codes. A one-step enrolment
 * locks out everybody who scanned the code into an app they then deleted.
 *
 * <p>Recovery codes are issued at the same moment, once, and stored hashed.
 * They are the answer to a lost phone; without them, support becomes the
 * weakest link in the authentication chain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private static final int BACKUP_CODE_COUNT = 10;

    private final UserSecurityService userSecurityService;
    private final TotpService totpService;
    private final VerificationService verificationService;
    private final SecurityEventService securityEvents;

    @Value("${sentinel.security.issuer:Tracelight}")
    private String issuer;

    /** The secret and its QR payload, shown once during enrolment. */
    public record Enrolment(String secret, String provisioningUri) {
    }

    /** Result of switching MFA on: the recovery codes, shown once. */
    public record Activation(List<String> backupCodes) {
    }

    /**
     * Starts enrolment. The secret is stored but MFA stays off until
     * {@link #activate} proves the user can produce a code from it.
     */
    @Transactional
    public Enrolment beginEnrolment(User user) {
        UserSecurity security = userSecurityService.forUser(user);
        if (security.isMfaEnabled()) {
            throw new BusinessException("MFA_ALREADY_ENABLED",
                    "Multi-factor authentication is already on. Turn it off before enrolling again.");
        }

        String secret = Secrets.base32Secret();
        security.setMfaSecret(secret);
        security.setMfaMethod(MfaMethod.TOTP);
        userSecurityService.save(security);

        return new Enrolment(secret, totpService.provisioningUri(secret, user.getEmail(), issuer));
    }

    /** Confirms the authenticator works, switches MFA on, and issues recovery codes. */
    @Transactional
    public Activation activate(User user, String code, HttpServletRequest request) {
        UserSecurity security = userSecurityService.forUser(user);
        if (security.getMfaSecret() == null) {
            throw new BusinessException("MFA_NOT_STARTED", "Start enrolment before confirming a code.");
        }
        if (!totpService.verify(security.getMfaSecret(), code)) {
            securityEvents.record(SecurityEventType.MFA_FAILED, user.getOrganizationId(),
                    user.getId(), user.getEmail(), request, Map.of("stage", "ENROLMENT"));
            throw new BusinessException("INVALID_MFA_CODE",
                    "That code does not match. Check the clock on your phone and try the next one.");
        }

        List<String> plaintext = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String codeValue = Secrets.token(5).toUpperCase();
            plaintext.add(codeValue.substring(0, 5) + "-" + codeValue.substring(5));
            hashes.add(Secrets.sha256(plaintext.get(i)));
        }

        security.setMfaEnabled(true);
        security.setMfaMethod(MfaMethod.TOTP);
        security.setMfaEnrolledAt(java.time.Instant.now());
        security.setBackupCodes(hashes);
        userSecurityService.save(security);

        securityEvents.record(SecurityEventType.MFA_ENABLED, user.getOrganizationId(),
                user.getId(), user.getEmail(), request, Map.of("method", "TOTP"));
        return new Activation(plaintext);
    }

    /**
     * Checks a code presented during sign-in or a step-up.
     *
     * <p>Accepts an authenticator code, an SMS code, or a recovery code. A
     * recovery code is consumed on use: a code that still works after being
     * used is a password with extra steps.
     */
    @Transactional
    public MfaMethod verify(User user, String code, HttpServletRequest request) {
        UserSecurity security = userSecurityService.forUser(user);
        if (!security.isMfaEnabled()) {
            throw new BusinessException("MFA_NOT_ENABLED", "This account has no second factor.");
        }

        if (totpService.verify(security.getMfaSecret(), code)) {
            securityEvents.record(SecurityEventType.MFA_SUCCEEDED, user.getOrganizationId(),
                    user.getId(), user.getEmail(), request, Map.of("method", "TOTP"));
            return MfaMethod.TOTP;
        }

        String normalised = code == null ? "" : code.trim().toUpperCase();
        String hash = Secrets.sha256(normalised);
        List<String> remaining = new ArrayList<>(
                security.getBackupCodes() == null ? List.of() : security.getBackupCodes());
        if (remaining.removeIf(stored -> Secrets.matches(hash, stored))) {
            security.setBackupCodes(remaining);
            userSecurityService.save(security);
            securityEvents.record(SecurityEventType.BACKUP_CODE_USED, user.getOrganizationId(),
                    user.getId(), user.getEmail(), request, Map.of("codesRemaining", remaining.size()));
            return MfaMethod.BACKUP_CODE;
        }

        // An SMS code is checked last because it costs a database read.
        if (security.isPhoneVerified()) {
            try {
                verificationService.consume(normalised, AuthTokenType.PHONE_OTP);
                securityEvents.record(SecurityEventType.MFA_SUCCEEDED, user.getOrganizationId(),
                        user.getId(), user.getEmail(), request, Map.of("method", "SMS"));
                return MfaMethod.SMS;
            } catch (BusinessException ignored) {
                // Falls through to the shared failure below, so a wrong SMS code
                // is indistinguishable from a wrong authenticator code.
            }
        }

        verificationService.chargeAttempt(user.getId(), AuthTokenType.PHONE_OTP);
        securityEvents.record(SecurityEventType.MFA_FAILED, user.getOrganizationId(),
                user.getId(), user.getEmail(), request, Map.of("stage", "VERIFY"));
        throw new BusinessException("INVALID_MFA_CODE", "That code is not valid. Try the next one.");
    }

    /** Sends a one-time code by text, for an account that enrolled a phone. */
    @Transactional
    public String sendSmsCode(User user, HttpServletRequest request) {
        UserSecurity security = userSecurityService.forUser(user);
        if (!security.isPhoneVerified() || security.getPhoneNumber() == null) {
            throw new BusinessException("NO_VERIFIED_PHONE",
                    "No verified phone number on this account.");
        }
        VerificationService.Issued issued = verificationService.issue(
                user, AuthTokenType.PHONE_OTP, security.getPhoneNumber(), true, request);
        verificationService.sendSmsCode(security.getPhoneNumber(), issued.plaintext(),
                AuthTokenType.PHONE_OTP.getLifetimeMinutes());
        securityEvents.record(SecurityEventType.PHONE_VERIFICATION_SENT, user.getOrganizationId(),
                user.getId(), user.getEmail(), request, Map.of("destination",
                        Secrets.maskPhone(security.getPhoneNumber())));
        return verificationService.codesAreExposed() ? issued.plaintext() : null;
    }

    /** Issues a fresh set of recovery codes, retiring the old ones. */
    @Transactional
    public List<String> regenerateBackupCodes(User user, HttpServletRequest request) {
        UserSecurity security = userSecurityService.forUser(user);
        if (!security.isMfaEnabled()) {
            throw new BusinessException("MFA_NOT_ENABLED",
                    "Turn on multi-factor authentication before generating recovery codes.");
        }

        List<String> plaintext = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String codeValue = Secrets.token(5).toUpperCase();
            String formatted = codeValue.substring(0, 5) + "-" + codeValue.substring(5);
            plaintext.add(formatted);
            hashes.add(Secrets.sha256(formatted));
        }
        security.setBackupCodes(hashes);
        userSecurityService.save(security);

        securityEvents.record(SecurityEventType.BACKUP_CODES_REGENERATED, user.getOrganizationId(),
                user.getId(), user.getEmail(), request, null);
        return plaintext;
    }

    /**
     * Turns MFA off. The caller must already have proved the password and a
     * current code — removing a factor is as sensitive as using one.
     */
    @Transactional
    public void disable(User user, HttpServletRequest request) {
        UserSecurity security = userSecurityService.forUser(user);
        security.setMfaEnabled(false);
        security.setMfaSecret(null);
        security.setMfaMethod(null);
        security.setMfaEnrolledAt(null);
        security.setBackupCodes(new ArrayList<>());
        userSecurityService.save(security);

        securityEvents.record(SecurityEventType.MFA_DISABLED, user.getOrganizationId(),
                user.getId(), user.getEmail(), request, null);
    }
}
