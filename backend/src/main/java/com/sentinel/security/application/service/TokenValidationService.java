package com.sentinel.security.application.service;

import com.sentinel.security.adapter.out.persistence.UserSecurityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "is this token still current?" on every authenticated request.
 *
 * <p>The honest implementation reads the user's token version from the
 * database each time, which puts a query in front of every API call. This
 * caches the version for a few seconds instead. The trade is explicit: a
 * revoked token can survive for at most the cache window, and a revocation
 * made through the platform clears the entry immediately, so the window only
 * applies to a change made directly in the database.
 */
@Service
@RequiredArgsConstructor
public class TokenValidationService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final UserSecurityRepository repository;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(int tokenVersion, Instant readAt) {
    }

    /**
     * True when the version stamped into the token still matches the account.
     *
     * <p>A user with no security row yet predates this subsystem; their tokens
     * are accepted so an upgrade does not sign everybody out.
     */
    public boolean isCurrent(UUID userId, Integer tokenVersion) {
        if (tokenVersion == null) return true;

        Cached cached = cache.get(userId);
        if (cached == null || cached.readAt().isBefore(Instant.now().minus(CACHE_TTL))) {
            Integer current = repository.findById(userId)
                    .map(security -> security.getTokenVersion())
                    .orElse(null);
            if (current == null) return true;
            cached = new Cached(current, Instant.now());
            cache.put(userId, cached);
        }
        return cached.tokenVersion() == tokenVersion;
    }

    /** Drops the cached version so the next request re-reads it. */
    public void invalidate(UUID userId) {
        cache.remove(userId);
    }
}
