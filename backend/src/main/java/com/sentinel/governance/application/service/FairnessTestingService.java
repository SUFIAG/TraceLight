package com.sentinel.governance.application.service;

import com.sentinel.governance.adapter.out.persistence.FairnessReportRepository;
import com.sentinel.governance.adapter.out.persistence.ScoreOutcomeRepository;
import com.sentinel.governance.domain.model.FairnessReport;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Disparate-impact testing on model outcomes.
 *
 * <p>Measures the approval rate per segment and compares each against the
 * best-treated segment. A ratio below 0.8 fails the four-fifths screen used
 * in fair-lending supervision, which is a trigger to investigate rather than
 * a finding of discrimination: a genuinely higher fraud rate in a corridor is
 * a legitimate reason for a lower approval rate, and the investigation is
 * what distinguishes the two.
 *
 * <p>Groups below a minimum volume are excluded. With twelve transactions, a
 * disparity ratio is noise, and reporting it would bury the real findings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FairnessTestingService {

    private static final double FOUR_FIFTHS = 0.80;
    private static final int MIN_GROUP_SIZE = 25;

    private final ScoreOutcomeRepository scoreRepository;
    private final FairnessReportRepository reportRepository;

    @Transactional
    public FairnessReport test(UUID orgId, String modelName, String attribute, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Object[]> rows = switch (attribute.toUpperCase(Locale.ROOT)) {
            case "CURRENCY" -> scoreRepository.outcomesByCurrency(orgId, since);
            default -> scoreRepository.outcomesByCountry(orgId, since);
        };

        Map<String, int[]> tally = new TreeMap<>();
        Map<String, Long> scoreSum = new HashMap<>();

        for (Object[] row : rows) {
            String group = String.valueOf(row[0]);
            TransactionStatus status = (TransactionStatus) row[1];
            int score = ((Number) row[2]).intValue();

            int[] counts = tally.computeIfAbsent(group, k -> new int[2]);
            counts[0]++;
            if (status == TransactionStatus.APPROVED) counts[1]++;
            scoreSum.merge(group, (long) score, Long::sum);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        double bestRate = 0;
        int sampleSize = 0;

        for (Map.Entry<String, int[]> entry : tally.entrySet()) {
            int total = entry.getValue()[0];
            if (total < MIN_GROUP_SIZE) continue;
            double approvalRate = (double) entry.getValue()[1] / total;
            bestRate = Math.max(bestRate, approvalRate);
            sampleSize += total;

            Map<String, Object> group = new HashMap<>();
            group.put("group", entry.getKey());
            group.put("volume", total);
            group.put("approvalRate", round(approvalRate));
            group.put("flagRate", round(1 - approvalRate));
            group.put("averageScore", Math.round((double) scoreSum.get(entry.getKey()) / total));
            groups.add(group);
        }

        double worstRatio = 1.0;
        for (Map<String, Object> group : groups) {
            double rate = (double) group.get("approvalRate");
            double ratio = bestRate == 0 ? 1.0 : rate / bestRate;
            group.put("ratioToBest", round(ratio));
            group.put("passesFourFifths", ratio >= FOUR_FIFTHS);
            worstRatio = Math.min(worstRatio, ratio);
        }

        groups.sort(Comparator.comparingDouble(g -> (double) g.get("ratioToBest")));

        String verdict = groups.size() < 2 ? "PASS"
                : worstRatio < 0.6 ? "FAIL"
                : worstRatio < FOUR_FIFTHS ? "REVIEW"
                : "PASS";

        String interpretation = buildInterpretation(groups, worstRatio, verdict, attribute);

        FairnessReport report = reportRepository.save(FairnessReport.builder()
                .organizationId(orgId)
                .modelName(modelName)
                .attribute(attribute.toUpperCase(Locale.ROOT))
                .groups(groups)
                .disparityRatio(round(worstRatio))
                .verdict(verdict)
                .groupsTested(groups.size())
                .sampleSize(sampleSize)
                .interpretation(interpretation)
                .build());

        log.info("Fairness test of {} by {} for org {}: worst ratio {} ({})",
                modelName, attribute, orgId, round(worstRatio), verdict);
        return report;
    }

    private String buildInterpretation(List<Map<String, Object>> groups, double worstRatio,
                                       String verdict, String attribute) {
        if (groups.size() < 2) {
            return "Not enough distinct groups above the minimum volume of " + MIN_GROUP_SIZE
                    + " to test for disparate impact by " + attribute.toLowerCase(Locale.ROOT) + ".";
        }
        Map<String, Object> worst = groups.get(0);
        return String.format(
                "Tested %d groups by %s. The lowest approval rate is %s at %.0f%%, which is %.0f%% of the "
                        + "best-treated group's rate. %s",
                groups.size(), attribute.toLowerCase(Locale.ROOT), worst.get("group"),
                ((double) worst.get("approvalRate")) * 100, worstRatio * 100,
                switch (verdict) {
                    case "FAIL" -> "This is well below the four-fifths screen and requires investigation "
                            + "and documented justification before the model continues in production.";
                    case "REVIEW" -> "This falls below the four-fifths screen. A documented explanation is needed, "
                            + "and a higher genuine fraud rate in this segment may be one.";
                    default -> "All groups clear the four-fifths screen.";
                });
    }

    private double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
