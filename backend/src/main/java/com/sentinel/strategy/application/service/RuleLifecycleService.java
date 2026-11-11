package com.sentinel.strategy.application.service;

import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.featurestore.application.service.FeatureComputationService;
import com.sentinel.strategy.adapter.out.persistence.RuleDefinitionRepository;
import com.sentinel.strategy.domain.model.RuleDefinition;
import com.sentinel.strategy.domain.model.RuleStatus;
import com.sentinel.strategy.domain.service.RuleDslEvaluator;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create, version, promote, and retire analyst-authored rules.
 *
 * <p>Rules are versioned rather than edited. Changing a live control in place
 * destroys the ability to answer "what did we have in force on 3 March", which
 * is the first question after any incident. Editing therefore creates a new
 * version and retires the old one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleLifecycleService {

    private final RuleDefinitionRepository ruleRepository;
    private final NaturalLanguageRuleService naturalLanguageRuleService;
    private final RuleDslEvaluator evaluator;
    private final FeatureComputationService featureComputationService;

    @Transactional
    public RuleDefinition createFromSentence(UUID orgId, String name, String sentence,
                                             int riskPoints, UUID authorId) {
        NaturalLanguageRuleService.Parsed parsed = naturalLanguageRuleService.parse(sentence);

        int version = ruleRepository.findByOrganizationIdAndNameOrderByVersionDesc(orgId, name).stream()
                .findFirst()
                .map(r -> r.getVersion() + 1)
                .orElse(1);

        RuleDefinition rule = ruleRepository.save(RuleDefinition.builder()
                .organizationId(orgId)
                .name(name)
                .description(parsed.explanation())
                .naturalLanguage(sentence)
                .definition(parsed.definition())
                .riskPoints(Math.max(1, Math.min(100, riskPoints)))
                .status(RuleStatus.DRAFT)
                .version(version)
                .trafficPercent(0)
                .hitCount(0)
                .truePositiveCount(0)
                .falsePositiveCount(0)
                .createdBy(authorId)
                .build());

        log.info("Rule '{}' v{} created from natural language by {}", name, version, authorId);
        return rule;
    }

    @Transactional
    public RuleDefinition createFromDefinition(UUID orgId, String name, String description,
                                               Map<String, Object> definition, int riskPoints, UUID authorId) {
        int version = ruleRepository.findByOrganizationIdAndNameOrderByVersionDesc(orgId, name).stream()
                .findFirst()
                .map(r -> r.getVersion() + 1)
                .orElse(1);

        return ruleRepository.save(RuleDefinition.builder()
                .organizationId(orgId)
                .name(name)
                .description(description == null ? naturalLanguageRuleService.explain(definition) : description)
                .definition(definition)
                .riskPoints(Math.max(1, Math.min(100, riskPoints)))
                .status(RuleStatus.DRAFT)
                .version(version)
                .trafficPercent(0)
                .createdBy(authorId)
                .build());
    }

    @Transactional(readOnly = true)
    public RuleDefinition get(UUID orgId, UUID ruleId) {
        return ruleRepository.findByIdAndOrganizationId(ruleId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("RuleDefinition", ruleId.toString()));
    }

    /**
     * Moves a rule along its lifecycle. Promotion to CHAMPION demotes the
     * previous champion of the same name, so two versions of one control can
     * never both be live.
     */
    @Transactional
    public RuleDefinition promote(UUID orgId, UUID ruleId, RuleStatus target, int trafficPercent, UUID approverId) {
        RuleDefinition rule = get(orgId, ruleId);

        if (target == RuleStatus.CHAMPION && rule.getStatus() == RuleStatus.DRAFT) {
            throw new BusinessException("SHADOW_REQUIRED",
                    "A rule must run in shadow or as a challenger before it becomes champion");
        }

        if (target == RuleStatus.CHAMPION) {
            ruleRepository.findByOrganizationIdAndNameOrderByVersionDesc(orgId, rule.getName()).stream()
                    .filter(other -> !other.getId().equals(ruleId) && other.getStatus() == RuleStatus.CHAMPION)
                    .forEach(other -> {
                        other.setStatus(RuleStatus.RETIRED);
                        ruleRepository.save(other);
                        log.info("Rule '{}' v{} retired on promotion of v{}",
                                other.getName(), other.getVersion(), rule.getVersion());
                    });
        }

        rule.setStatus(target);
        rule.setTrafficPercent(target == RuleStatus.CHALLENGER ? Math.max(1, Math.min(100, trafficPercent))
                : target == RuleStatus.CHAMPION ? 100 : 0);
        rule.setApprovedBy(approverId);
        rule.setPromotedAt(Instant.now());
        return ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<RuleDefinition> live(UUID orgId) {
        return ruleRepository.findByOrganizationIdAndStatusIn(orgId,
                List.of(RuleStatus.CHAMPION, RuleStatus.CHALLENGER, RuleStatus.SHADOW));
    }

    /**
     * Evaluates every live rule against one transaction.
     *
     * <p>Shadow rules are evaluated and their outcome returned, but the caller
     * is told not to apply them. That separation is what makes shadow mode
     * honest rather than a label on a switch nobody checks.
     */
    @Transactional
    public List<Evaluation> evaluateAll(UUID orgId, Transaction transaction) {
        List<RuleDefinition> liveRules = live(orgId);
        if (liveRules.isEmpty()) {
            // Computing a feature vector costs several queries. Nothing would
            // read it, so the payment path should not pay for it.
            return List.of();
        }

        Map<String, Object> features = featureComputationService.compute(orgId, transaction);
        List<Evaluation> evaluations = new ArrayList<>();

        for (RuleDefinition rule : liveRules) {
            if (rule.getStatus() == RuleStatus.CHALLENGER && !inChallengerSample(transaction, rule)) {
                continue;
            }
            RuleDslEvaluator.Result result = evaluator.evaluate(rule, features);
            if (result.triggered()) {
                rule.setHitCount(rule.getHitCount() + 1);
                ruleRepository.save(rule);
            }
            evaluations.add(new Evaluation(
                    rule.getId(), rule.getName(), rule.getStatus(), result.triggered(),
                    rule.getRiskPoints(), result.satisfiedConditions(),
                    rule.getStatus() != RuleStatus.SHADOW));
        }
        return evaluations;
    }

    /**
     * Deterministic sampling on the transaction id. The same transaction always
     * falls on the same side of the split, so a challenger's measured
     * performance is not polluted by re-evaluation.
     */
    private boolean inChallengerSample(Transaction transaction, RuleDefinition rule) {
        int bucket = Math.abs(transaction.getId().hashCode() % 100);
        return bucket < rule.getTrafficPercent();
    }

    /** Records whether a fired rule turned out to be right. Feeds precision over time. */
    @Transactional
    public void recordOutcome(UUID orgId, UUID ruleId, boolean wasFraud) {
        ruleRepository.findByIdAndOrganizationId(ruleId, orgId).ifPresent(rule -> {
            if (wasFraud) {
                rule.setTruePositiveCount(rule.getTruePositiveCount() + 1);
            } else {
                rule.setFalsePositiveCount(rule.getFalsePositiveCount() + 1);
            }
            ruleRepository.save(rule);
        });
    }

    /** One rule's verdict on one transaction, and whether it is allowed to count. */
    public record Evaluation(UUID ruleId, String ruleName, RuleStatus status, boolean triggered,
                             int riskPoints, List<String> satisfiedConditions, boolean applied) {
    }
}
