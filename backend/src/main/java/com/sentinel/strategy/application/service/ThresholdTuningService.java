package com.sentinel.strategy.application.service;

import com.sentinel.strategy.adapter.out.persistence.TuningAnalyticsRepository;
import com.sentinel.transaction.domain.model.TransactionStatus;
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
 * Finds the decision cut-off that costs the institution least.
 *
 * <p>Thresholds are usually round numbers somebody picked years ago. The right
 * number is whichever minimises total cost, and total cost has three parts
 * that pull against each other:
 *
 * <pre>
 *   fraud loss     value of fraud that slipped through below the cut-off
 *   friction cost  value of genuine payments stopped above it
 *   operating cost analyst time spent reviewing everything above it
 * </pre>
 *
 * <p>The weights are the institution's own business judgement, not ours. A
 * neobank chasing growth prices friction high; a private bank prices fraud
 * loss high. The same data produces different optimal thresholds, and that is
 * correct.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThresholdTuningService {

    /** Share of a stopped genuine payment's value treated as the cost of stopping it. */
    private static final double DEFAULT_FRICTION_WEIGHT = 0.05;
    /** Cost of one analyst review. */
    private static final double DEFAULT_REVIEW_COST = 8.0;

    private final TuningAnalyticsRepository analytics;

    @Transactional(readOnly = true)
    public TuningResult tune(UUID orgId, int days, Double frictionWeight, Double reviewCost) {
        double friction = frictionWeight == null ? DEFAULT_FRICTION_WEIGHT : frictionWeight;
        double review = reviewCost == null ? DEFAULT_REVIEW_COST : reviewCost;

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = analytics.scoredOutcomes(orgId, since);

        if (rows.isEmpty()) {
            return new TuningResult(null, List.of(),
                    "No scored transactions in the last " + days + " days, so there is nothing to tune against.");
        }

        List<ThresholdCost> curve = new ArrayList<>();
        for (int threshold = 30; threshold <= 95; threshold += 5) {
            curve.add(costAt(rows, threshold, friction, review));
        }

        ThresholdCost best = curve.stream()
                .min((a, b) -> Double.compare(a.totalCost(), b.totalCost()))
                .orElseThrow();

        String rationale = String.format(
                "Across %d scored transactions, a cut-off of %d minimises total cost at %.0f: "
                        + "%.0f in missed fraud, %.0f in friction on %d genuine payments, and %.0f in review time "
                        + "for %d alerts. Weights used: friction %.0f%% of stopped value, %.2f per review.",
                rows.size(), best.threshold(), best.totalCost(), best.fraudLoss(), best.frictionCost(),
                best.genuineStopped(), best.operatingCost(), best.alertVolume(), friction * 100, review);

        log.info("Threshold tuning for org {}: optimum {} (cost {})", orgId, best.threshold(), best.totalCost());
        return new TuningResult(best, curve, rationale);
    }

    private ThresholdCost costAt(List<Object[]> rows, int threshold, double frictionWeight, double reviewCost) {
        double fraudLoss = 0;
        double frictionCost = 0;
        int alerts = 0;
        int genuineStopped = 0;
        int fraudCaught = 0;
        int fraudMissed = 0;

        for (Object[] row : rows) {
            int score = ((Number) row[0]).intValue();
            TransactionStatus status = (TransactionStatus) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            boolean fraud = status == TransactionStatus.FLAGGED || status == TransactionStatus.REJECTED;

            if (score >= threshold) {
                alerts++;
                if (fraud) {
                    fraudCaught++;
                } else {
                    genuineStopped++;
                    frictionCost += amount.doubleValue() * frictionWeight;
                }
            } else if (fraud) {
                fraudMissed++;
                fraudLoss += amount.doubleValue();
            }
        }

        double operatingCost = alerts * reviewCost;
        return new ThresholdCost(threshold, round(fraudLoss), round(frictionCost), round(operatingCost),
                round(fraudLoss + frictionCost + operatingCost), alerts, genuineStopped, fraudCaught, fraudMissed);
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** The full cost curve, so the committee sees the trade rather than one number. */
    public record ThresholdCost(int threshold, double fraudLoss, double frictionCost, double operatingCost,
                                double totalCost, int alertVolume, int genuineStopped,
                                int fraudCaught, int fraudMissed) {
    }

    public record TuningResult(ThresholdCost recommended, List<ThresholdCost> curve, String rationale) {
    }
}
