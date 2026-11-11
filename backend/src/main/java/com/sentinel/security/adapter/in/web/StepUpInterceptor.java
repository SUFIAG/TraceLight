package com.sentinel.security.adapter.in.web;

import com.sentinel.common.config.AuthenticationDetails;
import com.sentinel.security.application.service.SecurityEventService;
import com.sentinel.security.application.service.SecurityProperties;
import com.sentinel.security.application.service.StepUpRequired;
import com.sentinel.security.application.service.UserSecurityService;
import com.sentinel.security.domain.model.SecurityEventType;
import com.sentinel.security.domain.model.UserSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.sentinel.common.exception.StepUpRequiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces {@link StepUpRequired}: an account with a second factor must have
 * used it recently before performing a sensitive action.
 *
 * <p>The check is on the session's last MFA time rather than the token, so
 * re-authenticating in one tab unlocks the action everywhere that device is
 * signed in, which is what a user expects.
 */
@Component
@RequiredArgsConstructor
public class StepUpInterceptor implements HandlerInterceptor {

    private final UserSecurityService userSecurityService;
    private final SecurityEventService securityEvents;
    private final SecurityProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)
                || !method.hasMethodAnnotation(StepUpRequired.class)) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return true;

        UUID userId = UUID.fromString((String) auth.getPrincipal());
        UserSecurity security;
        try {
            security = userSecurityService.require(userId);
        } catch (RuntimeException e) {
            // No posture row means the account predates MFA. Nothing to enforce.
            return true;
        }

        // Without a second factor there is nothing to step up to. The console
        // asks these users to enrol rather than blocking their work.
        if (!security.isMfaEnabled()) return true;

        Instant mfaAt = auth.getDetails() instanceof AuthenticationDetails details ? details.mfaAt() : null;
        Instant freshEnough = Instant.now().minus(Duration.ofMinutes(properties.getStepUpFreshnessMinutes()));

        if (mfaAt == null || mfaAt.isBefore(freshEnough)) {
            securityEvents.record(SecurityEventType.STEP_UP_REQUIRED, security.getOrganizationId(),
                    userId, null, request, Map.of("path", request.getRequestURI()));
            throw new StepUpRequiredException(
                    "Confirm your identity with a second factor before making this change.");
        }
        return true;
    }
}
