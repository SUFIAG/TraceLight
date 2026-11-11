package com.sentinel.strategy.application.service;

import com.sentinel.featurestore.application.service.FeatureComputationService;
import com.sentinel.strategy.adapter.out.persistence.RuleBacktestRepository;
import com.sentinel.strategy.domain.model.RuleBacktest;
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
 * Replays a rule over history and says whether to ship it.
 *
 * <p>"What happens if I change this rule?" is the question that currently
 * takes a bank six weeks and a data scientist. The answer here is a
 * same-session number, including the part vendors leave out: the operational
 * cost of the alerts the rule would create. A rule with excellent recall that
 * triples the queue is not a good rule.
 *
 * <p>Labels come from the recorded outcome of each transaction. FLAGGED and
 * REJECTED count as fraud, APPROVED as legitimate. That is an imperfect ground
 * truth — some approved transactions were fraud nobody noticed — so recall is
 * read as a floor, not as truth, and the recommendation text says so.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleBacktestService {

    /** Minutes an analyst spends on one alert. Used to price the rule's queue load. */
    private static final double MINUTES_PER_ALERT = 12.0;
    /** Precision below this means more noise than signal. */
    private static final double MIN_SHIPPABLE_PRECISION = 0.30;

    private final TransactionRepository transactionRepository;
    private final FeatureComputationService featureComputationService;
    private final RuleDslEvaluator evaluator;
    private final RuleBacktestRepository backtestRepository;

    @Transactional
    public RuleBacktest run(UUID orgId, RuleDefinition rule, int windowDays, int sampleLimit) {
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);

        List<Transaction> sample = transactionRepository
                .findByOrganizationId(orgId, PageRequest.of(0, sampleLimit, Sort.by("timestamp").descending()))
                .getContent()
                .stream()
                .filter(t -> t.getTimestamp().isAfter(since))
                .toList();

        int hits = 0;
        int truePositives = 0;
        int falsePositives = 0;
        int actualFraud = 0;
        BigDecimal valueCaught = BigDecimal.ZERO;
        BigDecimal valueDisrupted = BigDecimal.ZERO;
        List<String> sampleHits = new ArrayList<>();

        for (Transaction transaction : sample) {
            boolean labelledFraud = transaction.getStatus() == TransactionStatus.FLAGGED
                    || transaction.getStatus() == TransactionStatus.REJECTED;
            if (labelledFraud) actualFraud++;

            Map<String, Object> features = featureComputationService.compute(orgId, transaction);
            RuleDslEvaluator.Result result = evaluator.evaluate(rule, features);
            if (!result.triggered()) continue;

            hits++;
            if (labelledFraud) {
                truePositives++;
                valueCaught = valueCaught.add(transaction.getAmount());
            } else {
                falsePositives++;
                valueDisrupted = valueDisrupted.add(transaction.getAmount());
            }
            if (sampleHits.size() < 20) {
                sampleHits.add(transaction.getTransactionExternalId() + " — " + transaction.getAmount()
                        + " — " + String.join("; ", result.satisfiedConditions()));
            }
        }

        int falseNegatives = Math.max(0, actualFraud - truePositives);
        double precision = hits == 0 ? 0 : (double) truePositives / hits;
        double recall = actualFraud == 0 ? 0 : (double) truePositives / actualFraud;

        double alertsPerDay = windowDays == 0 ? hits : (double) hits / windowDays;
        double monthlyHours = alertsPerDay * 30 * MINUTES_PER_ALERT / 60.0;

        String recommendation = recommend(hits, precision, recall, monthlyHours);
        String reason = reason(hits, precision, recall, monthlyHours, sample.size(), actualFraud);

        RuleBacktest backtest = backtestRepository.save(RuleBacktest.builder()
                .organizationId(orgId)
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .transactionsEvaluated(sample.size())
                .hitCount(hits)
                .truePositives(truePositives)
                .falsePositives(falsePositives)
                .falseNegatives(falseNegatives)
                .precision(round(precision))
                .recall(round(recall))
                .valueCaught(valueCaught)
                .valueDisrupted(valueDisrupted)
                .monthlyReviewHours(Math.round(monthlyHours * 10) / 10.0)
                .windowDays(windowDays)
                .sampleHits(sampleHits)
                .recommendation(recommendation)
                .recommendationReason(reason)
                .build());

        log.info("Backtest of '{}' over {} transactions: {} hits, precision {}, recommendation {}",
                rule.getName(), sample.size(), hits, round(precision), recommendation);
        return backtest;
    }

    private String recommend(int hits, double precision, double recall, double monthlyHours) {
        if (hits == 0) return "DO_NOT_SHIP";
        if (precision >= 0.6 && monthlyHours <= 80) return "SHIP";
        if (precision >= MIN_SHIPPABLE_PRECISION) return "SHIP_IN_SHADOW";
        return "DO_NOT_SHIP";
    }

    private String reason(int hits, double precision, double recall,
                          double monthlyHours, int evaluated, int actualFraud) {
        if (hits == 0) {
            return "The rule fired on none of the " + evaluated + " transactions evaluated. "
                    + "Either the conditions are too tight or the features it uses are not populated.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Fired on ").append(hits).append(" of ").append(evaluated)
                .append(" transactions. Precision ").append(Math.round(precision * 100)).append("%");
        if (actualFraud > 0) {
            sb.append(", catching ").append(Math.round(recall * 100))
                    .append("% of transactions already labelled as fraud");
        }
        sb.append(". At current volumes it would add roughly ")
                .append(Math.round(monthlyHours)).append(" analyst hours a month. ");

        if (precision < MIN_SHIPPABLE_PRECISION) {
            sb.append("Most of what it catches is legitimate traffic, so it should not go live as written.");
        } else if (monthlyHours > 80) {
            sb.append("The queue load is high enough that it should run in shadow first.");
        } else {
            sb.append("The trade between catch rate and review cost is acceptable.");
        }
        sb.append(" Labels are taken from recorded outcomes, so recall is a floor rather than a true rate.");
        return sb.toString();
    }

    private double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
