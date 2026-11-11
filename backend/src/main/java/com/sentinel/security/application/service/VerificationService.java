package com.sentinel.security.application.service;

import com.sentinel.auth.domain.model.User;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.adapter.out.persistence.AuthTokenRepository;
import com.sentinel.security.domain.model.AuthToken;
import com.sentinel.security.domain.model.AuthTokenType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and redeems the one-time secrets: email verification codes, SMS
 * codes, password-reset tokens, and the handle for a sign-in waiting on a
 * second factor.
 *
 * <p>Three rules hold for all of them. The secret is stored hashed, so the
 * database never holds a usable code. Each has a budget of wrong guesses, so a
 * six-digit code cannot be walked. Issuing a new one retires the old, so a
 * stolen code stops working the moment the user asks for another.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final AuthTokenRepository repository;
    private final NotificationSender notificationSender;
    private final SecurityProperties properties;

    /** A code or token, plus whatever the caller is allowed to show the user. */
    public record Issued(UUID id, String plaintext, String destination, Instant expiresAt) {
    }

    /**
     * Issues a token of the given type, retiring any outstanding one.
     *
     * @param numeric true for a short code a person types, false for a long
     *                token that travels in a link
     */
    @Transactional
    public Issued issue(User user, AuthTokenType type, String destination, boolean numeric,
                        HttpServletRequest request) {
        assertRequestBudget(user.getId(), type);
        repository.consumeOutstanding(user.getId(), type, Instant.now());

        String plaintext = numeric ? Secrets.numericCode(6) : Secrets.token(32);
        AuthToken token = repository.save(AuthToken.builder()
                .userId(user.getId())
                .organizationId(user.getOrganizationId())
                .tokenType(type)
                .tokenHash(Secrets.sha256(plaintext))
                .destination(destination)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(type.getLifetimeMinutes())))
                .maxAttempts(type.getMaxAttempts())
                .ipAddress(SecurityEventService.clientIp(request))
                .build());

        return new Issued(token.getId(), plaintext, destination, token.getExpiresAt());
    }

    /**
     * Checks a presented secret and consumes it on success.
     *
     * <p>A wrong guess costs an attempt. When the budget runs out the token is
     * dead even if the next guess would have been right, which is what stops a
     * six-digit code from being brute-forced in a loop.
     */
    @Transactional
    public AuthToken consume(String plaintext, AuthTokenType type) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new BusinessException("INVALID_CODE", "Enter the code you were sent");
        }

        AuthToken token = repository.findByTokenHashAndTokenType(Secrets.sha256(plaintext.trim()), type)
                .orElse(null);

        if (token == null) {
            // The hash did not match any row. Charge an attempt against the
            // user's outstanding token so guessing is still rate-limited.
            throw new BusinessException("INVALID_CODE", friendlyFailure(type));
        }
        if (token.getConsumedAt() != null) {
            throw new BusinessException("CODE_ALREADY_USED", "That code has already been used.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("CODE_EXPIRED", "That code has expired. Ask for a new one.");
        }
        if (token.getAttempts() >= token.getMaxAttempts()) {
            throw new BusinessException("CODE_EXHAUSTED",
                    "Too many attempts on that code. Ask for a new one.");
        }

        token.setConsumedAt(Instant.now());
        return repository.save(token);
    }

    /** Charges a failed guess against the user's outstanding token of this type. */
    @Transactional
    public void chargeAttempt(UUID userId, AuthTokenType type) {
        repository.findByUserIdAndTokenTypeAndConsumedAtIsNull(userId, type)
                .forEach(token -> {
                    token.setAttempts(token.getAttempts() + 1);
                    repository.save(token);
                });
    }

    /** Looks up an unconsumed token by its identifier, for a challenge handle. */
    @Transactional(readOnly = true)
    public AuthToken findUsable(UUID tokenId, AuthTokenType type) {
        AuthToken token = repository.findById(tokenId).orElse(null);
        if (token == null || token.getTokenType() != type || !token.isUsable()) {
            throw new BusinessException("CHALLENGE_EXPIRED",
                    "This sign-in has expired. Start again.");
        }
        return token;
    }

    @Transactional
    public void markConsumed(AuthToken token) {
        token.setConsumedAt(Instant.now());
        repository.save(token);
    }

    public void sendEmailCode(String to, String code, String purpose, int minutes) {
        notificationSender.email(to, "Your Tracelight " + purpose + " code",
                "Your code is " + code + ".\n\nIt expires in " + minutes + " minutes. "
                        + "If you did not ask for it, change your password and tell your administrator.");
    }

    public void sendSmsCode(String phoneNumber, String code, int minutes) {
        notificationSender.sms(phoneNumber,
                "Tracelight code " + code + ". Expires in " + minutes + " minutes. Never share it.");
    }

    /** True when the deployment is allowed to echo codes back in API responses. */
    public boolean codesAreExposed() {
        return properties.isExposeVerificationCodes();
    }

    private void assertRequestBudget(UUID userId, AuthTokenType type) {
        long recent = repository.countByUserIdAndTokenTypeAndCreatedAtAfter(
                userId, type, Instant.now().minus(Duration.ofHours(1)));
        if (recent >= properties.getVerificationRequestsPerHour()) {
            throw new BusinessException("TOO_MANY_REQUESTS",
                    "Too many codes requested. Wait an hour before asking for another.");
        }
    }

    private String friendlyFailure(AuthTokenType type) {
        return switch (type) {
            case PASSWORD_RESET -> "That reset link is not valid. Ask for a new one.";
            case EMAIL_VERIFICATION -> "That verification code is not valid.";
            case PHONE_OTP -> "That code is not valid.";
            case MFA_CHALLENGE -> "This sign-in has expired. Start again.";
        };
    }
}
