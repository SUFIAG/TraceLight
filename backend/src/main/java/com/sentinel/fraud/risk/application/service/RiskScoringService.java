package com.sentinel.fraud.risk.application.service;

import com.sentinel.behavioral.device.application.service.DeviceService;
import com.sentinel.behavioral.velocity.application.service.VelocityAnalyzer;
import com.sentinel.fraud.risk.adapter.out.persistence.RiskScoreRepository;
import com.sentinel.fraud.risk.domain.model.RiskLevel;
import com.sentinel.fraud.risk.domain.model.RiskScore;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Produces the 0-100 risk score for a transaction.
 *
 * <p>The score is a <b>weighted behavioural base</b> raised by an
 * <b>evidence floor</b>:
 *
 * <pre>
 *   base  = Σ (dimension × weight)      // amount, velocity, location, device, corroboration
 *   floor = strongest triggered rule × corroboration multiplier
 *   score = max(base, floor)
 * </pre>
 *
 * <p>The floor exists because the weighted average alone cannot express a
 * decisive single signal. A transaction at a blacklisted merchant is fraud
 * regardless of how ordinary its other four dimensions look, but averaging that
 * 95 against four low dimensions produced a score in the fifties. Before this
 * change the maximum score any transaction could reach was 63, which made the
 * CRITICAL band (81-100) unreachable and left the rule engine's strongest
 * findings with no effect on the number an analyst actually sees.
 *
 * <p>The corroboration multiplier keeps a single unsupported rule from
 * dominating: one rule can only lift the score to 70% of its contribution,
 * two rules to 88%, three or more to the full value.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

    private static final double AMOUNT_WEIGHT = 0.28;
    private static final double VELOCITY_WEIGHT = 0.22;
    private static final double LOCATION_WEIGHT = 0.20;
    private static final double DEVICE_WEIGHT = 0.15;
    private static final double HISTORY_WEIGHT = 0.15;

    /** Index = number of triggered rules, capped at 3+. */
    private static final double[] CORROBORATION = { 0.0, 0.70, 0.88, 1.00 };

    private final RiskScoreRepository riskScoreRepository;
    private final DeviceService deviceService;
    private final VelocityAnalyzer velocityAnalyzer;

    @Transactional
    public RiskScore calculateAndSave(Transaction transaction, List<RuleResult> ruleResults) {
        List<RuleResult> triggered = ruleResults.stream().filter(RuleResult::isTriggered).toList();

        int amountRisk = calculateAmountRisk(transaction);
        int velocityRisk = velocityAnalyzer.calculateVelocityRisk(transaction);
        int locationRisk = calculateLocationRisk(ruleResults);
        int deviceRisk = deviceService.assessAndRecord(transaction);
        int historyRisk = calculateCorroborationRisk(triggered);

        int base = (int) Math.round(
                amountRisk * AMOUNT_WEIGHT +
                velocityRisk * VELOCITY_WEIGHT +
                locationRisk * LOCATION_WEIGHT +
                deviceRisk * DEVICE_WEIGHT +
                historyRisk * HISTORY_WEIGHT
        );

        int floor = evidenceFloor(triggered);
        int totalScore = Math.min(100, Math.max(0, Math.max(base, floor)));

        RiskScore riskScore = RiskScore.builder()
                .transactionId(transaction.getId())
                .score(totalScore)
                .level(RiskLevel.fromScore(totalScore))
                .amountRisk(amountRisk)
                .velocityRisk(velocityRisk)
                .locationRisk(locationRisk)
                .deviceRisk(deviceRisk)
                .historyRisk(historyRisk)
                .build();

        RiskScore saved = riskScoreRepository.save(riskScore);
        log.debug("Risk score {} ({}) for transaction {} — base={} floor={} rules={}",
                totalScore, saved.getLevel(), transaction.getId(), base, floor, triggered.size());
        return saved;
    }

    /**
     * The strongest triggered rule, discounted when nothing else corroborates it.
     */
    private int evidenceFloor(List<RuleResult> triggered) {
        if (triggered.isEmpty()) {
            return 0;
        }
        int strongest = triggered.stream()
                .mapToInt(RuleResult::getRiskContribution)
                .max()
                .orElse(0);
        double multiplier = CORROBORATION[Math.min(triggered.size(), CORROBORATION.length - 1)];
        return (int) Math.round(strongest * multiplier);
    }

    private int calculateAmountRisk(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        if (amount.compareTo(new BigDecimal("25000")) >= 0) return 100;
        if (amount.compareTo(new BigDecimal("10000")) >= 0) return 95;
        if (amount.compareTo(new BigDecimal("5000")) >= 0) return 70;
        if (amount.compareTo(new BigDecimal("2000")) >= 0) return 45;
        if (amount.compareTo(new BigDecimal("500")) >= 0) return 15;
        // Micro-amounts are the signature of card testing, not of low risk.
        if (amount.compareTo(new BigDecimal("5")) < 0) return 35;
        return 5;
    }

    private int calculateLocationRisk(List<RuleResult> ruleResults) {
        return ruleResults.stream()
                .filter(r -> r.isTriggered() && isLocationRule(r.getRuleName()))
                .mapToInt(RuleResult::getRiskContribution)
                .max()
                .orElse(5);
    }

    private boolean isLocationRule(String ruleName) {
        return "GEOGRAPHIC_ANOMALY".equals(ruleName) || "IMPOSSIBLE_TRAVEL".equals(ruleName);
    }

    /**
     * How many independent rules agree. Several weak signals pointing the same
     * way are stronger evidence than any one of them alone.
     */
    private int calculateCorroborationRisk(List<RuleResult> triggered) {
        return switch (Math.min(triggered.size(), 4)) {
            case 0 -> 5;
            case 1 -> 30;
            case 2 -> 60;
            case 3 -> 85;
            default -> 100;
        };
    }
}
