package com.sentinel.security.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets the response headers that constrain what a browser will do with an API
 * response.
 *
 * <p>This API returns JSON only, so the policy can be far stricter than a
 * page-serving application: nothing may be loaded, nothing may frame it, and
 * nothing may be sniffed into a different content type. Several of the attacks
 * these headers prevent — reflected content executing as script, clickjacking
 * an authenticated session, a referrer leaking a reset token to a third
 * party — need no bug in the application at all, only a missing header.
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // A JSON API loads nothing and frames nothing, so deny everything.
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy",
                "accelerometer=(), camera=(), geolocation=(), gyroscope=(), microphone=(), payment=(), usb=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");

        // Only meaningful over TLS, and harmful to send otherwise: a browser
        // would pin a development host to HTTPS it does not serve.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        // Nothing this API returns belongs in a shared cache, and most of it is
        // account-specific. Caching it is how one analyst sees another's queue.
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
