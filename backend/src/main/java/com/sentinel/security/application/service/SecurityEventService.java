package com.sentinel.security.application.service;

import com.sentinel.security.adapter.out.persistence.SecurityEventRepository;
import com.sentinel.security.domain.model.SecurityEvent;
import com.sentinel.security.domain.model.SecurityEventType;
import com.sentinel.security.domain.model.SecuritySeverity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the security log.
 *
 * <p>Events are written in their own transaction. A failed sign-in that rolls
 * back the caller's work must still leave its trace, and the reverse matters
 * more: a logging failure must never be the reason a lockout does not apply.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityEventService {

    private final SecurityEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityEventType type, UUID organizationId, UUID userId, String email,
                       HttpServletRequest request, Map<String, Object> detail) {
        try {
            SecurityEvent event = SecurityEvent.builder()
                    .organizationId(organizationId)
                    .userId(userId)
                    .email(email)
                    .eventType(type)
                    .severity(type.getSeverity())
                    .ipAddress(clientIp(request))
                    .userAgent(truncate(header(request, "User-Agent"), 512))
                    .detail(detail == null ? new HashMap<>() : new HashMap<>(detail))
                    .build();
            repository.save(event);

            if (type.getSeverity() == SecuritySeverity.HIGH || type.getSeverity() == SecuritySeverity.CRITICAL) {
                log.warn("Security event {} for user {} from {}", type, userId, event.getIpAddress());
            }
        } catch (Exception e) {
            // Losing a log line is bad. Failing the request that produced it is worse.
            log.error("Could not write security event {}: {}", type, e.getMessage());
        }
    }

    public void record(SecurityEventType type, UUID organizationId, UUID userId, HttpServletRequest request) {
        record(type, organizationId, userId, null, request, null);
    }

    /**
     * The caller's address, honouring a proxy header only when the platform is
     * told to trust one. Reading {@code X-Forwarded-For} unconditionally lets
     * any client forge its own source address and slip a per-IP rate limit.
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
