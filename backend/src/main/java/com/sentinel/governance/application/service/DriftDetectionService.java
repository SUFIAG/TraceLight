package com.sentinel.governance.application.service;

import com.sentinel.governance.adapter.out.persistence.DriftReportRepository;
import com.sentinel.governance.adapter.out.persistence.ScoreOutcomeRepository;
import com.sentinel.governance.domain.model.DriftReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Population stability monitoring.
 *
 * <pre>
 *   PSI = Σ (current% − baseline%) × ln(current% / baseline%)
 * </pre>
 *
 * <p>Industry convention reads the result as: below 0.10 stable, 0.10 to 0.25
 * worth watching, above 0.25 materially shifted and the model should be
 * revalidated. The per-bucket contributions are kept because the useful
 * finding is almost never "it drifted" but "the 70-to-80 band doubled", which
 * points at what changed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriftDetectionService {

    private static final int[] BUCKET_EDGES = {0, 20, 40, 60, 70, 80, 90, 101};
    /** Avoids a division by zero when a bucket is empty in one window. */
    private static final double EPSILON = 0.0001;
    private static final int MIN_SAMPLE = 30;

    private final ScoreOutcomeRepository scoreRepository;
    private final DriftReportRepository driftRepository;

    @Transactional
    public DriftReport detect(UUID orgId, String modelName, int baselineDays, int currentDays) {
        Instant now = Instant.now();
        Instant currentStart = now.minus(currentDays, ChronoUnit.DAYS);
        Instant baselineStart = currentStart.minus(baselineDays, ChronoUnit.DAYS);

        List<Integer> baseline = scoreRepository.scoresBetween(orgId, baselineStart, currentStart);
        List<Integer> current = scoreRepository.scoresBetween(orgId, currentStart, now);

        if (baseline.size() < MIN_SAMPLE || current.size() < MIN_SAMPLE) {
            return driftRepository.save(DriftReport.builder()
                    .organizationId(orgId)
                    .modelName(modelName)
                    .metric("SCORE_DISTRIBUTION")
                    .psi(0)
                    .status("STABLE")
                    .baselineCount(baseline.size())
                    .currentCount(current.size())
                    .baselineStart(baselineStart)
                    .currentStart(currentStart)
                    .buckets(List.of())
                    .interpretation("Not enough scored transactions to measure drift: "
                            + baseline.size() + " baseline and " + current.size() + " current, "
                            + "against a minimum of " + MIN_SAMPLE + " each.")
                    .build());
        }

        double[] baselineShare = distribution(baseline);
        double[] currentShare = distribution(current);

        double psi = 0;
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (int i = 0; i < baselineShare.length; i++) {
            double b = Math.max(baselineShare[i], EPSILON);
            double c = Math.max(currentShare[i], EPSILON);
            double contribution = (c - b) * Math.log(c / b);
            psi += contribution;

            Map<String, Object> bucket = new HashMap<>();
            bucket.put("range", BUCKET_EDGES[i] + "-" + (BUCKET_EDGES[i + 1] - 1));
            bucket.put("baselineShare", round(baselineShare[i]));
            bucket.put("currentShare", round(currentShare[i]));
            bucket.put("contribution", round(contribution));
            buckets.add(bucket);
        }

        String status = psi >= 0.25 ? "DRIFTED" : psi >= 0.10 ? "WATCH" : "STABLE";

        Map<String, Object> largest = buckets.stream()
                .max((a, b) -> Double.compare(
                        Math.abs((double) a.get("contribution")), Math.abs((double) b.get("contribution"))))
                .orElse(Map.of("range", "n/a"));

        String interpretation = String.format(
                "PSI %.3f (%s) comparing %d recent scores against %d baseline scores. "
                        + "The largest shift is in the %s band, which moved from %.1f%% to %.1f%% of volume. %s",
                psi, status, current.size(), baseline.size(), largest.get("range"),
                ((double) largest.getOrDefault("baselineShare", 0.0)) * 100,
                ((double) largest.getOrDefault("currentShare", 0.0)) * 100,
                "DRIFTED".equals(status)
                        ? "The scoring population has materially changed; the model should be revalidated."
                        : "WATCH".equals(status)
                        ? "Worth monitoring; no action required yet."
                        : "The population is stable.");

        DriftReport report = driftRepository.save(DriftReport.builder()
                .organizationId(orgId)
                .modelName(modelName)
                .metric("SCORE_DISTRIBUTION")
                .psi(round(psi))
                .status(status)
                .baselineCount(baseline.size())
                .currentCount(current.size())
                .baselineStart(baselineStart)
                .currentStart(currentStart)
                .buckets(buckets)
                .interpretation(interpretation)
                .build());

        log.info("Drift for {} in org {}: PSI {} ({})", modelName, orgId, round(psi), status);
        return report;
    }

    private double[] distribution(List<Integer> scores) {
        int[] counts = new int[BUCKET_EDGES.length - 1];
        for (int score : scores) {
            for (int i = 0; i < counts.length; i++) {
                if (score >= BUCKET_EDGES[i] && score < BUCKET_EDGES[i + 1]) {
                    counts[i]++;
                    break;
                }
            }
        }
        double[] shares = new double[counts.length];
        for (int i = 0; i < counts.length; i++) {
            shares[i] = (double) counts[i] / scores.size();
        }
        return shares;
    }

    private double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    /** Daily check. Drift found a month late is drift that already cost money. */
    @Scheduled(cron = "${sentinel.governance.drift-cron:0 30 2 * * *}")
    public void scheduledCheck() {
        log.debug("Scheduled drift check tick — run per organization from the governance console");
    }
}
