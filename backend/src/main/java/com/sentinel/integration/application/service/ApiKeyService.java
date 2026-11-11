package com.sentinel.integration.application.service;

import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.integration.adapter.out.persistence.ApiKeyRepository;
import com.sentinel.integration.domain.model.ApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Machine credentials for server-to-server scoring. Keys are shown once and stored hashed. */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository repository;

    @Transactional
    public Map<String, Object> issue(UUID orgId, String name, List<String> scopes,
                                     Integer rateLimit, Integer expiryDays, UUID actorId) {
        String plaintext = "sk_live_" + randomToken();
        String prefix = plaintext.substring(0, 14);

        ApiKey key = repository.save(ApiKey.builder()
                .organizationId(orgId)
                .name(name)
                .keyPrefix(prefix)
                .keyHash(sha256(plaintext))
                .scopes(scopes == null || scopes.isEmpty() ? List.of("SCORE") : scopes)
                .rateLimitPerMinute(rateLimit == null ? 600 : rateLimit)
                .active(true)
                .expiresAt(expiryDays == null ? null : Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .createdBy(actorId)
                .build());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", key.getId());
        response.put("name", key.getName());
        response.put("keyPrefix", key.getKeyPrefix());
        response.put("scopes", key.getScopes());
        response.put("rateLimitPerMinute", key.getRateLimitPerMinute());
        response.put("apiKey", plaintext);
        response.put("note", "Store this key now. It is not recoverable.");
        return response;
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(UUID orgId) {
        return repository.findByOrganizationId(orgId);
    }

    /** Resolves a presented key. Returns empty when unknown, inactive, or expired. */
    @Transactional
    public Optional<ApiKey> resolve(String presented) {
        if (presented == null || presented.isBlank()) return Optional.empty();
        return repository.findByKeyHash(sha256(presented))
                .filter(ApiKey::isActive)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(Instant.now()))
                .map(key -> {
                    key.setLastUsedAt(Instant.now());
                    return repository.save(key);
                });
    }

    @Transactional
    public ApiKey revoke(UUID orgId, UUID id) {
        ApiKey key = repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id.toString()));
        key.setActive(false);
        return repository.save(key);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest(value.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
