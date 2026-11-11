package com.sentinel.security.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.dto.ErrorDetails;
import com.sentinel.security.application.service.SecurityEventService;
import com.sentinel.security.application.service.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps how often one source can call the API.
 *
 * <p>Two tiers. A generous ceiling across the whole API stops a scraper or a
 * runaway client from exhausting the connection pool. A much tighter budget on
 * the credential endpoints is what actually blunts credential stuffing:
 * account lockout protects one account, but an attacker spreading ten guesses
 * across ten thousand accounts never trips it. The per-IP budget does.
 *
 * <p>Counters are in memory, which is correct for a single instance and
 * approximate behind a load balancer. A clustered deployment should point this
 * at Redis; the shape of the check does not change.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        final AtomicInteger count = new AtomicInteger();
        volatile Instant resetAt;

        Window(Duration length) {
            this.resetAt = Instant.now().plus(length);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = SecurityEventService.clientIp(request);

        Budget budget = budgetFor(path, request.getMethod());
        if (budget != null) {
            String key = budget.name() + "|" + ip;
            long retryAfter = consume(key, budget.limit(), budget.window());
            if (retryAfter > 0) {
                reject(response, retryAfter, budget.message());
                return;
            }
        }

        // The global ceiling applies to everything under /api, including paths
        // that already have a tighter budget of their own.
        if (path.startsWith("/api/")) {
            long retryAfter = consume("global|" + ip,
                    properties.getGlobalRequestsPerMinute(), Duration.ofMinutes(1));
            if (retryAfter > 0) {
                reject(response, retryAfter, "Too many requests. Slow down and try again shortly.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private record Budget(String name, int limit, Duration window, String message) {
    }

    private Budget budgetFor(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) return null;

        if (path.endsWith("/auth/login") || path.endsWith("/auth/mfa/verify")) {
            return new Budget("login", properties.getLoginAttemptsPerWindow(),
                    Duration.ofMinutes(properties.getLoginWindowMinutes()),
                    "Too many sign-in attempts from this network. Wait a few minutes and try again.");
        }
        if (path.endsWith("/auth/register")) {
            return new Budget("register", 5, Duration.ofHours(1),
                    "Too many accounts created from this network. Try again later.");
        }
        if (path.contains("/auth/password/forgot") || path.contains("/auth/password/reset")) {
            return new Budget("reset", 5, Duration.ofHours(1),
                    "Too many password reset requests. Try again later.");
        }
        if (path.contains("/security/email/send") || path.contains("/security/phone/send")
                || path.contains("/auth/mfa/sms")) {
            return new Budget("verification", properties.getVerificationRequestsPerHour(),
                    Duration.ofHours(1),
                    "Too many codes requested. Wait before asking for another.");
        }
        return null;
    }

    /** Returns seconds to wait when the budget is spent, or 0 when the call may proceed. */
    private long consume(String key, int limit, Duration length) {
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.resetAt.isBefore(Instant.now())) {
                return new Window(length);
            }
            return existing;
        });

        if (window.count.incrementAndGet() > limit) {
            return Math.max(1, Duration.between(Instant.now(), window.resetAt).getSeconds());
        }
        return 0;
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds, String message)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                ErrorDetails.of("RATE_LIMITED", message, null, null)));
    }

    /**
     * Windows that have already reset hold no information. Clearing them keeps
     * the map proportional to active clients rather than to every address that
     * has ever called.
     */
    public void evictStaleWindows() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> entry.getValue().resetAt.isBefore(now.minus(Duration.ofMinutes(5))));
    }
}
