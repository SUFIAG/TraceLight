package com.sentinel.common.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints and parses access tokens.
 *
 * <p>Every token carries four claims that make revocation possible before
 * expiry: a {@code jti} so a single token can be denied, a {@code sid} tying
 * it to a device session, a {@code tv} token version that a password or role
 * change can invalidate wholesale, and {@code mfa} recording whether a second
 * factor was satisfied — which is what a step-up check reads.
 *
 * <p>Refresh tokens are not JWTs. They are opaque strings owned by
 * {@code SessionService}, because a refresh token must be revocable the
 * instant a device is removed, and a self-contained token cannot be.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private static final String ISSUER = "tracelight";

    private final SecretKey signingKey;
    private final long accessTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.access-token-expiration:3600}") long accessTokenExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /** Issues an access token bound to a session. */
    public String generateAccessToken(UUID userId, String email, UUID organizationId, String role,
                                      UUID sessionId, int tokenVersion, boolean mfaSatisfied) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("email", email)
                .claim("organizationId", organizationId.toString())
                .claim("role", role)
                .claim("sid", sessionId == null ? null : sessionId.toString())
                .claim("tv", tokenVersion)
                .claim("mfa", mfaSatisfied)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpiration)))
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().requireIssuer(ISSUER).verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Routine: the client refreshes and retries. Not worth an error line.
            log.debug("Expired JWT presented");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected JWT: {}", e.getMessage());
        }
        return false;
    }

    public Claims getClaims(String token) {
        return Jwts.parser().requireIssuer(ISSUER).verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getOrganizationIdFromToken(String token) {
        return getClaims(token).get("organizationId", String.class);
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
