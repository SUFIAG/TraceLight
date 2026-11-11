package com.sentinel.friction.application.service;

import com.sentinel.behavioral.device.adapter.out.persistence.DeviceRepository;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.friction.adapter.out.persistence.FrictionDecisionRepository;
import com.sentinel.friction.adapter.out.persistence.FrictionPolicyRepository;
import com.sentinel.friction.domain.model.FrictionDecision;
import com.sentinel.friction.domain.model.FrictionMethod;
import com.sentinel.friction.domain.model.FrictionPolicy;
import com.sentinel.scam.adapter.out.persistence.VulnerableCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Chooses the lightest control that is strong enough.
 *
 * <p>Friction is not free and it is not symmetric. The same one-time code
 * that mildly annoys one customer makes another abandon the payment and go to
 * a competitor, and the institution never learns which. So the default is
 * nothing, and every additional step has to be earned by the risk on the
 * specific payment in front of us.
 *
 * <p>Two adjustments to the matched policy:
 *
 * <ul>
 *   <li>A customer under adapted protection gets a stronger control than the
 *       score alone would justify, because the harm is greater.</li>
 *   <li>A trusted device drops the control a step, because the strongest
 *       evidence of who somebody is tends to be the phone they have carried
 *       for three years.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FrictionOrchestrationService {

    private final FrictionPolicyRepository policyRepository;
    private final FrictionDecisionRepository decisionRepository;
    private final VulnerableCustomerRepository vulnerableRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public FrictionDecision decide(UUID orgId, String userId, UUID transactionId, String channel,
                                   int riskScore, BigDecimal amount, String deviceFingerprint) {
        List<String> reasons = new ArrayList<>();

        boolean vulnerable = vulnerableRepository
                .findByOrganizationIdAndUserIdAndActiveTrue(orgId, userId).isPresent();
        boolean trustedDevice = isTrustedDevice(orgId, userId, deviceFingerprint);

        FrictionPolicy matched = match(orgId, channel, riskScore, amount, vulnerable, trustedDevice);
        FrictionMethod method;

        if (matched == null) {
            method = fallbackLadder(riskScore);
            reasons.add("No policy matched; applied the default ladder for a score of " + riskScore);
        } else {
            method = matched.getMethod();
            reasons.add("Policy '" + matched.getName() + "' matched for scores "
                    + matched.getMinScore() + "-" + matched.getMaxScore());
        }

        if (vulnerable && method.getCost() < FrictionMethod.PUSH_APPROVAL.getCost()) {
            method = FrictionMethod.PUSH_APPROVAL;
            reasons.add("Raised to in-app approval: the customer is under adapted protection");
        }

        if (trustedDevice && method != FrictionMethod.BLOCK && !vulnerable) {
            FrictionMethod relaxed = relax(method);
            if (relaxed != method) {
                reasons.add("Lowered from " + method.getLabel() + ": the device is already trusted");
                method = relaxed;
            }
        }

        FrictionDecision decision = decisionRepository.save(FrictionDecision.builder()
                .organizationId(orgId)
                .userId(userId)
                .transactionId(transactionId)
                .channel(channel == null ? "ANY" : channel)
                .riskScore(riskScore)
                .amount(amount)
                .method(method)
                .policyId(matched == null ? null : matched.getId())
                .policyName(matched == null ? "default-ladder" : matched.getName())
                .reasons(reasons)
                .outcome(method == FrictionMethod.NONE ? "NOT_REQUIRED" : "PENDING")
                .build());

        log.debug("Friction for {} at score {}: {}", userId, riskScore, method);
        return decision;
    }

    private FrictionPolicy match(UUID orgId, String channel, int riskScore, BigDecimal amount,
                                 boolean vulnerable, boolean trustedDevice) {
        for (FrictionPolicy policy : policyRepository.findByOrganizationIdAndEnabledTrueOrderByPriorityAsc(orgId)) {
            if (riskScore < policy.getMinScore() || riskScore > policy.getMaxScore()) continue;
            if (!"ANY".equalsIgnoreCase(policy.getChannel())
                    && channel != null && !policy.getChannel().equalsIgnoreCase(channel)) continue;
            if (policy.getMinAmount() != null
                    && (amount == null || amount.compareTo(policy.getMinAmount()) < 0)) continue;
            if (policy.isVulnerableOnly() && !vulnerable) continue;
            if (policy.isSkipIfTrustedDevice() && trustedDevice) continue;
            return policy;
        }
        return null;
    }

    /** Used when no policy is configured, so the platform is never silently open. */
    private FrictionMethod fallbackLadder(int riskScore) {
        if (riskScore >= 90) return FrictionMethod.CALLBACK;
        if (riskScore >= 75) return FrictionMethod.BIOMETRIC;
        if (riskScore >= 55) return FrictionMethod.PUSH_APPROVAL;
        if (riskScore >= 35) return FrictionMethod.PASSIVE_BIOMETRIC;
        return FrictionMethod.NONE;
    }

    private FrictionMethod relax(FrictionMethod method) {
        return switch (method) {
            case CALLBACK, VIDEO_VERIFICATION -> FrictionMethod.BIOMETRIC;
            case KBA, OTP_SMS, BIOMETRIC -> FrictionMethod.PUSH_APPROVAL;
            case PUSH_APPROVAL -> FrictionMethod.PASSIVE_BIOMETRIC;
            default -> method;
        };
    }

    private boolean isTrustedDevice(UUID orgId, String userId, String fingerprint) {
        if (fingerprint == null) return false;
        return deviceRepository.findByOrganizationIdAndUserIdOrderByLastSeenDesc(orgId, userId).stream()
                .anyMatch(device -> fingerprint.equals(device.getFingerprint()));
    }

    /** Records how the challenge ended. Abandonment is the cost side of the ledger. */
    @Transactional
    public FrictionDecision complete(UUID orgId, UUID decisionId, String outcome) {
        FrictionDecision decision = decisionRepository.findByIdAndOrganizationId(decisionId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("FrictionDecision", decisionId.toString()));
        decision.setOutcome(outcome);
        decision.setCompletedAt(Instant.now());
        return decisionRepository.save(decision);
    }

    @Transactional
    public FrictionPolicy savePolicy(UUID orgId, FrictionPolicy incoming) {
        Optional<FrictionPolicy> existing = incoming.getId() == null
                ? Optional.empty()
                : policyRepository.findByIdAndOrganizationId(incoming.getId(), orgId);

        FrictionPolicy policy = existing.orElseGet(() -> FrictionPolicy.builder()
                .organizationId(orgId)
                .build());

        policy.setName(incoming.getName());
        policy.setChannel(incoming.getChannel() == null ? "ANY" : incoming.getChannel());
        policy.setMinScore(incoming.getMinScore());
        policy.setMaxScore(incoming.getMaxScore());
        policy.setMinAmount(incoming.getMinAmount());
        policy.setMethod(incoming.getMethod());
        policy.setVulnerableOnly(incoming.isVulnerableOnly());
        policy.setSkipIfTrustedDevice(incoming.isSkipIfTrustedDevice());
        policy.setPriority(incoming.getPriority());
        policy.setEnabled(incoming.isEnabled());
        return policyRepository.save(policy);
    }
}
