package com.sentinel.fraud.application.service;

import com.sentinel.ai.application.service.ExplanationService;
import com.sentinel.alert.application.service.AlertService;
import com.sentinel.alert.domain.model.FraudAlert;
import com.sentinel.audit.application.service.AuditService;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.behavioral.profile.application.service.ProfileService;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.fraud.risk.application.service.RiskScoringService;
import com.sentinel.fraud.risk.domain.model.RiskScore;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.fraud.rules.domain.service.RuleEngine;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.application.service.TransactionService;
import com.sentinel.transaction.domain.model.Transaction;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sentinel.integration.application.service.WebhookService;
import com.sentinel.mule.application.service.BeneficiaryRiskService;
import com.sentinel.strategy.application.service.RuleLifecycleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudAnalysisService {

    private final TransactionRepository transactionRepository;
    private final RuleEngine ruleEngine;
    private final RiskScoringService riskScoringService;
    private final AlertService alertService;
    private final ExplanationService explanationService;
    private final TransactionService transactionService;
    private final AuditService auditService;
    private final ProfileService profileService;
    private final RuleLifecycleService ruleLifecycleService;
    private final BeneficiaryRiskService beneficiaryRiskService;
    private final WebhookService webhookService;

    @Transactional
    public void analyzeTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId.toString()));

        List<RuleResult> allResults = new ArrayList<>(ruleEngine.evaluate(transaction));
        // Analyst-authored rules join the built-in ones on equal terms. Shadow
        // rules are evaluated and recorded but never allowed to affect the score.
        allResults.addAll(dynamicRuleResults(transaction));

        List<RuleResult> triggeredRules = allResults.stream().filter(RuleResult::isTriggered).toList();

        RiskScore riskScore = riskScoringService.calculateAndSave(transaction, allResults);

        // Fold this transaction into the user's behavioral baseline.
        profileService.recordTransaction(transaction);

        auditService.logAction(transaction.getOrganizationId(), transactionId,
                AuditAction.TRANSACTION_ANALYZED, EntityType.TRANSACTION);

        if (riskScore.getScore() >= 60) {
            transactionService.updateStatus(transactionId, TransactionStatus.FLAGGED);

            FraudAlert alert = alertService.createAlertIfNeeded(transaction, riskScore, triggeredRules);
            if (alert != null) {
                explanationService.generateExplanation(alert, transaction, riskScore, triggeredRules);
                webhookService.publish(transaction.getOrganizationId(), "ALERT_CREATED", Map.of(
                        "alertId", alert.getId(),
                        "transactionId", transactionId,
                        "severity", alert.getSeverity().name(),
                        "score", riskScore.getScore(),
                        "userId", transaction.getUserId()));
            }

            // The destination account is where scam money repeats across victims,
            // so a flagged payment is a reason to re-judge its beneficiary.
            beneficiaryRiskService.score(transaction.getOrganizationId(), transaction.getMerchantId());

            auditService.logAction(transaction.getOrganizationId(), transactionId,
                    AuditAction.TRANSACTION_FLAGGED, EntityType.TRANSACTION);
        } else {
            transactionService.updateStatus(transactionId, TransactionStatus.APPROVED);
            auditService.logAction(transaction.getOrganizationId(), transactionId,
                    AuditAction.TRANSACTION_APPROVED, EntityType.TRANSACTION);
        }

        log.info("Transaction {} analyzed: score={} level={} triggeredRules={}",
                transactionId, riskScore.getScore(), riskScore.getLevel(), triggeredRules.size());
    }

    /**
     * Converts analyst-authored rule evaluations into the same {@link RuleResult}
     * shape the built-in rules produce, so the scorer treats both identically.
     * Shadow rules are filtered out: they are measured, never applied.
     */
    private List<RuleResult> dynamicRuleResults(Transaction transaction) {
        try {
            return ruleLifecycleService.evaluateAll(transaction.getOrganizationId(), transaction).stream()
                    .filter(RuleLifecycleService.Evaluation::applied)
                    .map(evaluation -> evaluation.triggered()
                            ? RuleResult.triggered(evaluation.ruleName(), evaluation.riskPoints(),
                            String.join("; ", evaluation.satisfiedConditions()))
                            : RuleResult.pass(evaluation.ruleName()))
                    .toList();
        } catch (Exception e) {
            // A broken custom rule must never stop a transaction being scored.
            log.error("Dynamic rule evaluation failed for transaction {}: {}",
                    transaction.getId(), e.getMessage());
            return List.of();
        }
    }
}
