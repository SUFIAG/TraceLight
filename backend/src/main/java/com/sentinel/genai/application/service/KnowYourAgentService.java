package com.sentinel.genai.application.service;

import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.genai.adapter.out.persistence.AgentActivityRepository;
import com.sentinel.genai.adapter.out.persistence.AiAgentIdentityRepository;
import com.sentinel.genai.domain.model.AgentActivity;
import com.sentinel.genai.domain.model.AiAgentIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Know Your Agent.
 *
 * <p>Four questions, in order, on every agent-initiated request:
 *
 * <ol>
 *   <li><b>Is this agent registered?</b> An unregistered agent is not
 *       necessarily malicious, but it holds no delegation, so it cannot act.</li>
 *   <li><b>Is the delegation still valid?</b> Not revoked, not expired.</li>
 *   <li><b>Is this inside what the customer authorised?</b> Scope, merchant,
 *       per-transaction limit, daily limit.</li>
 *   <li><b>Is it behaving as delegated?</b> An agent authorised to buy
 *       groceries weekly that starts sending money to new beneficiaries at
 *       three in the morning has either been compromised or repurposed.</li>
 * </ol>
 *
 * <p>Question four is the one nobody else asks yet, and it is the one that
 * matters once agents are common: the credential stays valid long after the
 * agent stops being trustworthy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowYourAgentService {

    private final AiAgentIdentityRepository identityRepository;
    private final AgentActivityRepository activityRepository;

    @Transactional
    public AiAgentIdentity register(UUID orgId, AiAgentIdentity incoming) {
        AiAgentIdentity identity = identityRepository
                .findByOrganizationIdAndAgentKey(orgId, incoming.getAgentKey())
                .orElseGet(() -> AiAgentIdentity.builder()
                        .organizationId(orgId)
                        .agentKey(incoming.getAgentKey())
                        .transactionCount(0)
                        .behaviourDriftScore(0)
                        .build());

        identity.setAgentName(incoming.getAgentName());
        identity.setOperator(incoming.getOperator());
        identity.setPrincipalUserId(incoming.getPrincipalUserId());
        identity.setKeyFingerprint(fingerprint(incoming.getKeyFingerprint()));
        identity.setScopes(incoming.getScopes() == null ? List.of() : incoming.getScopes());
        identity.setAllowedMerchants(incoming.getAllowedMerchants());
        identity.setPerTransactionLimit(incoming.getPerTransactionLimit());
        identity.setDailyLimit(incoming.getDailyLimit());
        identity.setStatus("ACTIVE");
        // A delegation with no end date is a standing liability, so one is imposed.
        identity.setExpiresAt(incoming.getExpiresAt() == null
                ? Instant.now().plus(365, ChronoUnit.DAYS)
                : incoming.getExpiresAt());

        AiAgentIdentity saved = identityRepository.save(identity);
        log.info("Agent {} registered for principal {} with scopes {}",
                saved.getAgentKey(), saved.getPrincipalUserId(), saved.getScopes());
        return saved;
    }

    @Transactional
    public Authorization authorize(UUID orgId, String agentKey, String scope,
                                   String merchantId, BigDecimal amount) {
        List<String> reasons = new ArrayList<>();

        AiAgentIdentity identity = identityRepository
                .findByOrganizationIdAndAgentKey(orgId, agentKey)
                .orElse(null);

        if (identity == null) {
            return deny(orgId, null, agentKey, null, scope, merchantId, amount,
                    List.of("Agent is not registered and holds no delegation"));
        }

        if (!"ACTIVE".equals(identity.getStatus())) {
            return deny(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(), scope, merchantId, amount,
                    List.of("Agent delegation is " + identity.getStatus().toLowerCase()));
        }

        if (identity.getExpiresAt() != null && identity.getExpiresAt().isBefore(Instant.now())) {
            identity.setStatus("EXPIRED");
            identityRepository.save(identity);
            return deny(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(), scope, merchantId, amount,
                    List.of("Agent delegation expired on " + identity.getExpiresAt()));
        }

        if (identity.getScopes() != null && !identity.getScopes().isEmpty()
                && !identity.getScopes().contains(scope)) {
            return deny(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(), scope, merchantId, amount,
                    List.of("Scope " + scope + " was not delegated; permitted scopes are " + identity.getScopes()));
        }

        if (identity.getAllowedMerchants() != null && !identity.getAllowedMerchants().isEmpty()
                && merchantId != null && !identity.getAllowedMerchants().contains(merchantId)) {
            return deny(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(), scope, merchantId, amount,
                    List.of("Merchant " + merchantId + " is outside the delegated merchant list"));
        }

        if (amount != null && identity.getPerTransactionLimit() != null
                && amount.compareTo(identity.getPerTransactionLimit()) > 0) {
            return deny(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(), scope, merchantId, amount,
                    List.of("Amount " + amount + " exceeds the per-transaction limit of "
                            + identity.getPerTransactionLimit()));
        }

        if (amount != null && identity.getDailyLimit() != null) {
            BigDecimal spent = activityRepository.spentSince(orgId, agentKey,
                    Instant.now().minus(1, ChronoUnit.DAYS));
            if (spent.add(amount).compareTo(identity.getDailyLimit()) > 0) {
                return deny(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(), scope, merchantId, amount,
                        List.of("Daily limit of " + identity.getDailyLimit() + " would be exceeded; "
                                + spent + " already spent today"));
            }
        }

        int drift = assessDrift(orgId, identity, merchantId, amount, reasons);
        identity.setBehaviourDriftScore(drift);
        identity.setTransactionCount(identity.getTransactionCount() + 1);
        identity.setLastSeenAt(Instant.now());
        identityRepository.save(identity);

        String decision = drift >= 70 ? "STEP_UP" : "ALLOW";
        if (drift >= 70) {
            reasons.add("Behaviour has drifted far from the delegated pattern; the principal must confirm");
        }
        if (reasons.isEmpty()) {
            reasons.add("Within delegated scope and limits");
        }

        record(orgId, identity.getId(), agentKey, identity.getPrincipalUserId(),
                scope, merchantId, amount, decision, reasons);

        return new Authorization(decision, identity.getPrincipalUserId(), drift, reasons);
    }

    /**
     * Drift is measured against the agent's own established pattern, not
     * against a population. The question is whether this agent is still doing
     * what it has always done.
     */
    private int assessDrift(UUID orgId, AiAgentIdentity identity, String merchantId,
                            BigDecimal amount, List<String> reasons) {
        List<AgentActivity> history = activityRepository
                .findByOrganizationIdAndAgentKeyOrderByCreatedAtDesc(orgId, identity.getAgentKey());
        if (history.size() < 5) {
            return 0;
        }

        int drift = 0;

        boolean seenMerchant = merchantId == null || history.stream()
                .anyMatch(a -> merchantId.equals(a.getMerchantId()));
        if (!seenMerchant) {
            reasons.add("First payment to " + merchantId + " by this agent");
            drift += 30;
        }

        if (amount != null) {
            BigDecimal average = history.stream()
                    .map(a -> a.getAmount() == null ? BigDecimal.ZERO : a.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(history.size()), 2, java.math.RoundingMode.HALF_UP);
            if (average.signum() > 0 && amount.compareTo(average.multiply(BigDecimal.valueOf(5))) > 0) {
                reasons.add("Amount is more than five times this agent's own average of " + average);
                drift += 40;
            }
        }

        long recent = history.stream()
                .filter(a -> a.getCreatedAt().isAfter(Instant.now().minus(1, ChronoUnit.HOURS)))
                .count();
        if (recent >= 20) {
            reasons.add(recent + " requests from this agent in the last hour");
            drift += 35;
        }

        return Math.min(100, drift);
    }

    @Transactional
    public AiAgentIdentity revoke(UUID orgId, UUID identityId, String reason) {
        AiAgentIdentity identity = identityRepository.findByIdAndOrganizationId(identityId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("AiAgentIdentity", identityId.toString()));
        identity.setStatus("REVOKED");
        identity.setRevokedReason(reason);
        log.warn("Agent {} revoked: {}", identity.getAgentKey(), reason);
        return identityRepository.save(identity);
    }

    private Authorization deny(UUID orgId, UUID identityId, String agentKey, String principal,
                               String scope, String merchantId, BigDecimal amount, List<String> reasons) {
        record(orgId, identityId, agentKey, principal, scope, merchantId, amount, "DENY", reasons);
        return new Authorization("DENY", principal, 100, reasons);
    }

    private void record(UUID orgId, UUID identityId, String agentKey, String principal, String scope,
                        String merchantId, BigDecimal amount, String decision, List<String> reasons) {
        activityRepository.save(AgentActivity.builder()
                .organizationId(orgId)
                .agentIdentityId(identityId)
                .agentKey(agentKey)
                .principalUserId(principal)
                .requestedScope(scope)
                .merchantId(merchantId)
                .amount(amount)
                .decision(decision)
                .reasons(reasons)
                .build());
    }

    /** Only the fingerprint is kept. The platform never holds the agent's key material. */
    private String fingerprint(String keyMaterial) {
        if (keyMaterial == null) return "unset";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    public record Authorization(String decision, String principalUserId, int driftScore, List<String> reasons) {
    }
}
