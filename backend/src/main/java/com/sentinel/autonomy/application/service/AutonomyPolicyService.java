package com.sentinel.autonomy.application.service;

import com.sentinel.autonomy.adapter.out.persistence.AgentRunRepository;
import com.sentinel.autonomy.adapter.out.persistence.AutonomyPolicyRepository;
import com.sentinel.autonomy.domain.model.AgentActionType;
import com.sentinel.autonomy.domain.model.AutonomyLevel;
import com.sentinel.autonomy.domain.model.AutonomyPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The gate every autonomous action passes through.
 *
 * <p>Agents never decide for themselves whether they are allowed to act. They
 * state what they want to do and how confident they are, and this service
 * answers yes or no against the policy the institution configured. Keeping the
 * check in one place means a change to the risk appetite is one row, not a
 * code change in every agent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutonomyPolicyService {

    private final AutonomyPolicyRepository policyRepository;
    private final AgentRunRepository agentRunRepository;

    /** The configured policy, or the action's conservative default. */
    @Transactional(readOnly = true)
    public AutonomyPolicy policyFor(UUID orgId, AgentActionType action) {
        return policyRepository.findByOrganizationIdAndActionType(orgId, action)
                .orElseGet(() -> AutonomyPolicy.builder()
                        .organizationId(orgId)
                        .actionType(action)
                        .level(action.getDefaultLevel())
                        .minConfidence(80)
                        .maxRiskScore(40)
                        .maxAmount(new BigDecimal("5000"))
                        .requiresMakerChecker(false)
                        .dailyActionCap(null)
                        .build());
    }

    @Transactional(readOnly = true)
    public List<AutonomyPolicy> list(UUID orgId) {
        List<AutonomyPolicy> configured = policyRepository.findByOrganizationId(orgId);
        List<AutonomyPolicy> all = new ArrayList<>(configured);
        for (AgentActionType action : AgentActionType.values()) {
            boolean present = configured.stream().anyMatch(p -> p.getActionType() == action);
            if (!present) all.add(policyFor(orgId, action));
        }
        all.sort((a, b) -> a.getActionType().name().compareTo(b.getActionType().name()));
        return all;
    }

    @Transactional
    public AutonomyPolicy save(UUID orgId, AutonomyPolicy incoming, UUID actorId) {
        AutonomyPolicy policy = policyRepository
                .findByOrganizationIdAndActionType(orgId, incoming.getActionType())
                .orElseGet(() -> AutonomyPolicy.builder()
                        .organizationId(orgId)
                        .actionType(incoming.getActionType())
                        .build());

        policy.setLevel(incoming.getLevel());
        policy.setMaxAmount(incoming.getMaxAmount());
        policy.setMaxRiskScore(incoming.getMaxRiskScore());
        policy.setMinConfidence(incoming.getMinConfidence());
        policy.setRequiresMakerChecker(incoming.isRequiresMakerChecker());
        policy.setDailyActionCap(incoming.getDailyActionCap());
        policy.setUpdatedBy(actorId);

        log.info("Autonomy for {} set to {} by {}", policy.getActionType(), policy.getLevel(), actorId);
        return policyRepository.save(policy);
    }

    /**
     * Decides whether an agent may execute an action unaided.
     *
     * @param amount    transaction value at stake, or null when not applicable
     * @param riskScore risk of the item being acted on, or null when not applicable
     */
    @Transactional(readOnly = true)
    public Decision evaluate(UUID orgId, AgentActionType action, int confidence,
                             BigDecimal amount, Integer riskScore) {
        AutonomyPolicy policy = policyFor(orgId, action);

        if (policy.getLevel() == AutonomyLevel.OFF) {
            return Decision.denied("Autonomy for " + action.getLabel() + " is disabled");
        }
        if (policy.getLevel() == AutonomyLevel.SUGGEST_ONLY) {
            return Decision.denied("Policy allows a recommendation only; a human must act");
        }
        if (policy.isRequiresMakerChecker()) {
            return Decision.denied("Maker-checker is required for " + action.getLabel());
        }
        if (policy.getMinConfidence() != null && confidence < policy.getMinConfidence()) {
            return Decision.denied("Confidence " + confidence + " is below the required "
                    + policy.getMinConfidence());
        }

        if (policy.getLevel() == AutonomyLevel.FULL_AUTONOMY) {
            return allowedUnlessCapped(orgId, action, policy);
        }

        if (riskScore != null && policy.getMaxRiskScore() != null && riskScore > policy.getMaxRiskScore()) {
            return Decision.denied("Risk score " + riskScore + " exceeds the autonomous limit of "
                    + policy.getMaxRiskScore());
        }
        if (amount != null && policy.getMaxAmount() != null && amount.compareTo(policy.getMaxAmount()) > 0) {
            return Decision.denied("Amount " + amount + " exceeds the autonomous limit of " + policy.getMaxAmount());
        }

        return allowedUnlessCapped(orgId, action, policy);
    }

    /**
     * The daily cap is a circuit breaker, not a tuning knob. If an agent starts
     * closing alerts at ten times its usual rate, something upstream is wrong,
     * and the cap stops it before the queue is emptied incorrectly.
     */
    private Decision allowedUnlessCapped(UUID orgId, AgentActionType action, AutonomyPolicy policy) {
        if (policy.getDailyActionCap() == null) {
            return Decision.permit();
        }
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        long today = agentRunRepository.countExecutedSince(orgId, action, since);
        if (today >= policy.getDailyActionCap()) {
            return Decision.denied("Daily cap of " + policy.getDailyActionCap() + " for "
                    + action.getLabel() + " has been reached");
        }
        return Decision.permit();
    }

    /** Whether the action may proceed, and if not, why not — verbatim into the audit trail. */
    public record Decision(boolean allowed, String reason) {

        public static Decision permit() {
            return new Decision(true, null);
        }

        public static Decision denied(String reason) {
            return new Decision(false, reason);
        }
    }
}
