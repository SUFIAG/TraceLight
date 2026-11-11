package com.sentinel.strategy.application.service;

import com.sentinel.featurestore.application.service.FeatureComputationService;
import com.sentinel.strategy.domain.model.RuleDefinition;
import com.sentinel.strategy.domain.service.RuleDslEvaluator;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retroactive analysis: what the current controls would have caught in
 * history that was approved at the time.
 *
 * <p>This is the proof-of-value exercise. An institution sends twelve months
 * of data, the platform runs today's rule set over payments that were waved
 * through, and the output is a list of transactions with a reason attached.
 * It is also an honest exercise, which is why the result is reported as
 * "would have been flagged for review" rather than "fraud we found" — without
 * the chargeback outcomes, nobody can claim more than that.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetroAnalysisService {

    private final TransactionRepository transactionRepository;
    private final FeatureComputationService featureComputationService;
    private final RuleLifecycleService ruleLifecycleService;
    private final RuleDslEvaluator evaluator;

    @Transactional(readOnly = true)
    public RetroResult analyse(UUID orgId, int days, int sampleLimit) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<RuleDefinition> rules = ruleLifecycleService.live(orgId);

        List<Transaction> approved = transactionRepository
                .findByOrganizationIdAndStatus(orgId, TransactionStatus.APPROVED,
                        PageRequest.of(0, sampleLimit, Sort.by("timestamp").descending()))
                .getContent().stream()
                .filter(t -> t.getTimestamp().isAfter(since))
                .toList();

        List<Finding> findings = new ArrayList<>();
        BigDecimal exposure = BigDecimal.ZERO;

        for (Transaction transaction : approved) {
            Map<String, Object> features = featureComputationService.compute(orgId, transaction);
            List<String> reasons = new ArrayList<>();
            int points = 0;

            for (RuleDefinition rule : rules) {
                RuleDslEvaluator.Result result = evaluator.evaluate(rule, features);
                if (result.triggered()) {
                    points += rule.getRiskPoints();
                    reasons.add(rule.getName() + ": " + String.join("; ", result.satisfiedConditions()));
                }
            }

            if (reasons.isEmpty()) continue;

            findings.add(new Finding(
                    transaction.getId(),
                    transaction.getTransactionExternalId(),
                    transaction.getUserId(),
                    transaction.getMerchantId(),
                    transaction.getAmount(),
                    transaction.getTimestamp(),
                    Math.min(100, points),
                    reasons));
            exposure = exposure.add(transaction.getAmount());
        }

        findings.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<Finding> top = findings.stream().limit(200).toList();

        String summary = String.format(
                "Replayed %d live rules over %d approved transactions from the last %d days. "
                        + "%d would be flagged for review today, representing %s in value. "
                        + "These were approved at the time, so this is reviewable exposure rather than confirmed loss.",
                rules.size(), approved.size(), days, findings.size(), exposure);

        log.info("Retro analysis for org {}: {} of {} approved transactions would flag today",
                orgId, findings.size(), approved.size());

        return new RetroResult(approved.size(), findings.size(), exposure, rules.size(), top, summary);
    }

    public record Finding(UUID transactionId, String externalId, String userId, String merchantId,
                          BigDecimal amount, Instant timestamp, int score, List<String> reasons) {
    }

    public record RetroResult(int transactionsReplayed, int wouldFlagCount, BigDecimal exposure,
                              int rulesApplied, List<Finding> findings, String summary) {
    }
}
