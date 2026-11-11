package com.sentinel.security.adapter.in.web;

import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.application.service.UserSecurityService;
import com.sentinel.security.domain.model.UserSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * Keeps an account whose password must change from doing anything else.
 *
 * <p>Two cases need this: a one-time password handed over by an
 * administrator, and a password an administrator has retired after a
 * suspected compromise. In both, reporting the requirement and then serving
 * every endpoint anyway would make it advisory, which is the same as absent.
 *
 * <p>The exempt list is the minimum needed to comply: read your own account,
 * change the password, and sign out.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeInterceptor implements HandlerInterceptor {

    private static final List<String> ALLOWED = List.of(
            "/api/v1/auth/password/change",
            "/api/v1/auth/password/policy",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            "/api/v1/auth/refresh",
            "/api/v1/security/overview");

    private final UserSecurityService userSecurityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return true;

        String path = request.getRequestURI();
        if (ALLOWED.stream().anyMatch(path::startsWith)) return true;

        UserSecurity security;
        try {
            security = userSecurityService.require(UUID.fromString((String) auth.getPrincipal()));
        } catch (RuntimeException e) {
            return true;
        }

        if (security.isMustChangePassword() || userSecurityService.passwordExpired(security)) {
            throw new BusinessException("PASSWORD_CHANGE_REQUIRED",
                    "Set a new password before continuing.");
        }
        return true;
    }
}
