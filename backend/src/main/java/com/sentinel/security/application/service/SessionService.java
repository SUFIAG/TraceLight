package com.sentinel.security.application.service;

import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.adapter.out.persistence.RevokedTokenRepository;
import com.sentinel.security.adapter.out.persistence.UserSessionRepository;
import com.sentinel.security.domain.model.RevokedToken;
import com.sentinel.security.domain.model.SecurityEventType;
import com.sentinel.security.domain.model.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Owns signed-in devices and the refresh tokens that keep them signed in.
 *
 * <p>Refresh tokens are opaque random strings, stored hashed, and rotated on
 * every use. Rotation is what makes a stolen token detectable: the thief and
 * the real user cannot both hold the current token, so whoever presents the
 * retired one announces the theft. When that happens every session for the
 * user is killed rather than just the suspect one, because by then it is not
 * known which party is which.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final SecurityEventService securityEvents;
    private final SecurityProperties properties;

    @Value("${jwt.refresh-token-expiration:604800}")
    private long refreshTokenSeconds;

    /** A new or rotated session, with the refresh token the caller must return. */
    public record Established(UserSession session, String refreshToken) {
    }

    @Transactional
    public Established create(UUID userId, UUID organizationId, boolean mfaSatisfied,
                              HttpServletRequest request) {
        enforceSessionCap(userId);

        String refreshToken = Secrets.token(32);
        UserSession session = sessionRepository.save(UserSession.builder()
                .userId(userId)
                .organizationId(organizationId)
                .refreshTokenHash(Secrets.sha256(refreshToken))
                .ipAddress(SecurityEventService.clientIp(request))
                .userAgent(userAgent(request))
                .deviceLabel(deviceLabel(userAgent(request)))
                .mfaAt(mfaSatisfied ? Instant.now() : null)
                .expiresAt(Instant.now().plusSeconds(refreshTokenSeconds))
                .build());

        securityEvents.record(SecurityEventType.SESSION_CREATED, organizationId, userId, null, request,
                Map.of("device", String.valueOf(session.getDeviceLabel())));
        return new Established(session, refreshToken);
    }

    /**
     * Exchanges a refresh token for the next one.
     *
     * @throws BusinessException when the token is unknown, retired, expired,
     *                           idle past its limit, or replayed
     */
    @Transactional
    public Established rotate(String presented, HttpServletRequest request) {
        String hash = Secrets.sha256(presented);

        UserSession session = sessionRepository.findByRefreshTokenHash(hash).orElse(null);
        if (session == null) {
            sessionRepository.findByPreviousTokenHash(hash).ifPresent(compromised -> {
                revokeAll(compromised.getUserId(), "REFRESH_TOKEN_REUSED");
                securityEvents.record(SecurityEventType.REFRESH_TOKEN_REUSED,
                        compromised.getOrganizationId(), compromised.getUserId(), null, request,
                        Map.of("sessionId", String.valueOf(compromised.getId())));
            });
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Your session has ended. Sign in again.");
        }

        if (session.getRevokedAt() != null) {
            throw new BusinessException("SESSION_REVOKED", "This session was ended. Sign in again.");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("SESSION_EXPIRED", "Your session has expired. Sign in again.");
        }
        if (isIdle(session)) {
            revoke(session, "IDLE_TIMEOUT");
            throw new BusinessException("SESSION_IDLE",
                    "You were signed out after a period of inactivity. Sign in again.");
        }

        String next = Secrets.token(32);
        session.setPreviousTokenHash(session.getRefreshTokenHash());
        session.setRefreshTokenHash(Secrets.sha256(next));
        session.setLastSeenAt(Instant.now());
        session.setIpAddress(SecurityEventService.clientIp(request));
        sessionRepository.save(session);

        return new Established(session, next);
    }

    @Transactional(readOnly = true)
    public List<UserSession> listForUser(UUID userId) {
        return sessionRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .toList();
    }

    @Transactional
    public void revokeById(UUID userId, UUID sessionId, HttpServletRequest request) {
        UserSession session = sessionRepository.findById(sessionId)
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "No such session"));
        revoke(session, "USER_REVOKED");
        securityEvents.record(SecurityEventType.SESSION_REVOKED, session.getOrganizationId(),
                userId, null, request, Map.of("sessionId", sessionId.toString()));
    }

    @Transactional
    public int revokeAll(UUID userId, String reason) {
        return sessionRepository.revokeAllForUser(userId, Instant.now(), reason);
    }

    /** Ends every session except the one making the request. */
    @Transactional
    public int revokeOthers(UUID userId, UUID keepSessionId, HttpServletRequest request) {
        int revoked = 0;
        for (UserSession session : sessionRepository.findByUserIdAndRevokedAtIsNull(userId)) {
            if (session.getId().equals(keepSessionId)) continue;
            revoke(session, "USER_REVOKED_OTHERS");
            revoked++;
        }
        if (revoked > 0) {
            securityEvents.record(SecurityEventType.ALL_SESSIONS_REVOKED, null, userId, null, request,
                    Map.of("sessionsEnded", revoked));
        }
        return revoked;
    }

    /** Records that a second factor was satisfied on this device, for step-up. */
    @Transactional
    public void markMfaSatisfied(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setMfaAt(Instant.now());
            sessionRepository.save(session);
        });
    }

    @Transactional
    public void touch(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            // Only write when the stamp is meaningfully stale, so a busy console
            // does not turn every API call into a database write.
            if (session.getLastSeenAt().isBefore(Instant.now().minus(Duration.ofMinutes(5)))) {
                session.setLastSeenAt(Instant.now());
                sessionRepository.save(session);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> find(UUID sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /** True when the session is still valid for authenticating a request. */
    public boolean isUsable(UserSession session) {
        return session.isActive() && !isIdle(session);
    }

    /* ---------- Access token denylist ---------- */

    @Transactional
    public void denyAccessToken(String jti, UUID userId, Instant expiresAt, String reason) {
        if (jti == null) return;
        revokedTokenRepository.save(RevokedToken.builder()
                .jti(jti)
                .userId(userId)
                .expiresAt(expiresAt == null ? Instant.now().plus(Duration.ofHours(1)) : expiresAt)
                .reason(reason)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean isAccessTokenDenied(String jti) {
        return jti != null && revokedTokenRepository.existsById(jti);
    }

    /** Expired rows prove nothing and only slow the denylist down. */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeExpired() {
        int tokens = revokedTokenRepository.deleteExpired(Instant.now());
        int sessions = sessionRepository.deleteExpired(Instant.now().minus(Duration.ofDays(30)));
        if (tokens + sessions > 0) {
            log.debug("Purged {} revoked tokens and {} expired sessions", tokens, sessions);
        }
    }

    /* ---------- Internals ---------- */

    private void revoke(UserSession session, String reason) {
        session.setRevokedAt(Instant.now());
        session.setRevokedReason(reason);
        sessionRepository.save(session);
    }

    private boolean isIdle(UserSession session) {
        return session.getLastSeenAt()
                .isBefore(Instant.now().minus(Duration.ofMinutes(properties.getSessionIdleMinutes())));
    }

    /**
     * Keeps the number of live devices bounded. An account with hundreds of
     * sessions is either shared or compromised, and neither is improved by
     * letting the list grow.
     */
    private void enforceSessionCap(UUID userId) {
        List<UserSession> active = sessionRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(UserSession::isActive)
                .sorted(Comparator.comparing(UserSession::getLastSeenAt))
                .toList();
        int excess = active.size() - properties.getMaxSessionsPerUser() + 1;
        for (int i = 0; i < excess && i < active.size(); i++) {
            revoke(active.get(i), "SESSION_LIMIT");
        }
    }

    private String userAgent(HttpServletRequest request) {
        if (request == null) return null;
        String agent = request.getHeader("User-Agent");
        if (agent == null) return null;
        return agent.length() > 512 ? agent.substring(0, 512) : agent;
    }

    /**
     * A short, human label for the session list. Parsed from the user agent
     * because "Chrome on Windows" is something a user can recognise and
     * "Mozilla/5.0 (Windows NT 10.0; Win64; x64)..." is not.
     */
    private String deviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown device";

        String browser = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("OPR/") ? "Opera"
                : userAgent.contains("Chrome/") ? "Chrome"
                : userAgent.contains("Firefox/") ? "Firefox"
                : userAgent.contains("Safari/") ? "Safari"
                : userAgent.startsWith("curl") ? "Command line"
                : "Browser";

        String platform = userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Macintosh") || userAgent.contains("Mac OS") ? "macOS"
                : userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Linux") ? "Linux"
                : "Unknown platform";

        return browser + " on " + platform;
    }
}
