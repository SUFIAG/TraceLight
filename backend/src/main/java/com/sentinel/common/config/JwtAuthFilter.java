package com.sentinel.common.config;

import com.sentinel.security.application.service.SessionService;
import com.sentinel.security.application.service.TokenValidationService;
import com.sentinel.security.domain.model.UserSession;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Turns a bearer token into an authenticated request, and refuses the four
 * ways a structurally valid token can still be worthless:
 *
 * <ol>
 *   <li>its {@code jti} is on the denylist — the user signed out;</li>
 *   <li>its token version is stale — the password, role, or MFA changed;</li>
 *   <li>its session was revoked — the device was removed;</li>
 *   <li>its session went idle past the limit.</li>
 * </ol>
 *
 * <p>Without these a signed-out token keeps working until it expires, which is
 * the single most common gap in a JWT deployment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;
    private final TokenValidationService tokenValidationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtTokenProvider.getClaims(token);
            UUID userId = UUID.fromString(claims.getSubject());

            if (claims.getId() != null && sessionService.isAccessTokenDenied(claims.getId())) {
                filterChain.doFilter(request, response);
                return;
            }
            if (!tokenValidationService.isCurrent(userId, claims.get("tv", Integer.class))) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID sessionId = claims.get("sid", String.class) == null
                    ? null : UUID.fromString(claims.get("sid", String.class));
            Instant mfaAt = null;
            if (sessionId != null) {
                UserSession session = sessionService.find(sessionId).orElse(null);
                if (session == null || !sessionService.isUsable(session)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                mfaAt = session.getMfaAt();
                sessionService.touch(sessionId);
            }

            String role = claims.get("role", String.class);
            String orgId = claims.get("organizationId", String.class);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), orgId, authorities);
            auth.setDetails(new AuthenticationDetails(
                    sessionId,
                    Boolean.TRUE.equals(claims.get("mfa", Boolean.class)),
                    mfaAt,
                    claims.get("email", String.class)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            // A malformed claim set is an unauthenticated request, not a 500.
            log.warn("Could not establish authentication from token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
