package com.sentinel.mule.application.service;

import com.sentinel.mule.adapter.out.persistence.BeneficiaryAnalyticsRepository;
import com.sentinel.mule.adapter.out.persistence.BeneficiaryRiskRepository;
import com.sentinel.mule.domain.model.BeneficiaryRisk;
import com.sentinel.mule.domain.model.MuleBand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scores receiving accounts for mule behaviour.
 *
 * <p>Five independent signals, each capped, then summed and clamped. Summing
 * rather than averaging is deliberate: mule accounts are defined by the
 * <em>combination</em> of high fan-in, small credits, and a short life, and an
 * average would dilute each of them against the dimensions that look normal.
 *
 * <pre>
 *   fan-in        0-40   distinct unrelated payers
 *   smurfing      0-20   share of credits below the small-value threshold
 *   burst         0-15   many credits inside a short activity window
 *   dispersion    0-15   payers spread across countries
 *   velocity      0-10   credits per active day
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryRiskService {

    /** Credits below this are treated as structured, not as ordinary small payments. */
    private static final BigDecimal SMALL_CREDIT_THRESHOLD = new BigDecimal("500");
    private static final int OBSERVATION_DAYS = 30;
    /** Below this many payers the pattern is indistinguishable from a normal merchant. */
    private static final long MIN_PAYERS_FOR_SWEEP = 5;

    private final BeneficiaryRiskRepository beneficiaryRiskRepository;
    private final BeneficiaryAnalyticsRepository analytics;

    @Transactional(readOnly = true)
    public Optional<BeneficiaryRisk> find(UUID orgId, String beneficiaryId) {
        return beneficiaryRiskRepository.findByOrganizationIdAndBeneficiaryId(orgId, beneficiaryId);
    }

    /**
     * The number the payment flow asks for. Returns 0 when the beneficiary has
     * never been scored, so an unknown payee does not inherit risk it has not
     * earned — novelty is handled separately by the scam engine.
     */
    @Transactional(readOnly = true)
    public int riskOf(UUID orgId, String beneficiaryId) {
        if (beneficiaryId == null) return 0;
        return beneficiaryRiskRepository.findByOrganizationIdAndBeneficiaryId(orgId, beneficiaryId)
                .map(BeneficiaryRisk::getScore)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public boolean isConfirmedMule(UUID orgId, String beneficiaryId) {
        if (beneficiaryId == null) return false;
        return beneficiaryRiskRepository.findByOrganizationIdAndBeneficiaryId(orgId, beneficiaryId)
                .map(b -> b.getBand() == MuleBand.CONFIRMED)
                .orElse(false);
    }

    @Transactional
    public BeneficiaryRisk score(UUID orgId, String beneficiaryId) {
        Instant since = Instant.now().minus(OBSERVATION_DAYS, ChronoUnit.DAYS);

        long fanIn = analytics.countDistinctPayers(orgId, beneficiaryId, since);
        long credits = analytics.countCredits(orgId, beneficiaryId, since);
        BigDecimal total = analytics.sumCredited(orgId, beneficiaryId, since);
        long smallCredits = analytics.countSmallCredits(orgId, beneficiaryId, since, SMALL_CREDIT_THRESHOLD);
        long countries = analytics.countDistinctPayerCountries(orgId, beneficiaryId, since);
        int windowDays = activityWindowDays(orgId, beneficiaryId);

        double smallRatio = credits == 0 ? 0.0 : (double) smallCredits / credits;
        List<String> reasons = new ArrayList<>();

        int fanInPoints = scoreFanIn(fanIn, reasons);
        int smurfingPoints = scoreSmurfing(smallRatio, credits, reasons);
        int burstPoints = scoreBurst(credits, windowDays, reasons);
        int dispersionPoints = scoreDispersion(countries, reasons);
        int velocityPoints = scoreVelocity(credits, windowDays, reasons);

        BeneficiaryRisk existing = beneficiaryRiskRepository
                .findByOrganizationIdAndBeneficiaryId(orgId, beneficiaryId)
                .orElse(null);
        boolean confirmed = existing != null && existing.isExternallyConfirmed();
        if (confirmed) {
            reasons.add("Confirmed as a mule account by an external source");
        }

        int score = Math.min(100,
                fanInPoints + smurfingPoints + burstPoints + dispersionPoints + velocityPoints);
        if (confirmed) score = 100;

        BeneficiaryRisk risk = existing != null ? existing : BeneficiaryRisk.builder()
                .organizationId(orgId)
                .beneficiaryId(beneficiaryId)
                .externallyConfirmed(false)
                .build();

        risk.setScore(score);
        risk.setBand(MuleBand.fromScore(score, confirmed));
        risk.setFanIn((int) fanIn);
        risk.setCreditCount((int) credits);
        risk.setTotalCredited(total == null ? BigDecimal.ZERO : total);
        risk.setSmallCreditRatio(round(smallRatio));
        risk.setActivityWindowDays(windowDays);
        risk.setDistinctCountries((int) countries);
        risk.setReasons(reasons.isEmpty() ? List.of("No mule indicators in the last 30 days") : reasons);
        risk.setLastSeen(Instant.now());
        if (risk.getFirstSeen() == null) risk.setFirstSeen(Instant.now());

        BeneficiaryRisk saved = beneficiaryRiskRepository.save(risk);
        log.debug("Beneficiary {} scored {} ({}) fanIn={} credits={}",
                beneficiaryId, score, saved.getBand(), fanIn, credits);
        return saved;
    }

    /**
     * Rescores every beneficiary that has enough inbound traffic to be worth
     * judging. Run on a schedule or on demand from the mule console.
     */
    @Transactional
    public List<BeneficiaryRisk> sweep(UUID orgId, int limit) {
        Instant since = Instant.now().minus(OBSERVATION_DAYS, ChronoUnit.DAYS);
        List<Object[]> rows = analytics.topFanIn(orgId, since, MIN_PAYERS_FOR_SWEEP);
        List<BeneficiaryRisk> scored = new ArrayList<>();
        for (Object[] row : rows) {
            if (scored.size() >= limit) break;
            scored.add(score(orgId, (String) row[0]));
        }
        log.info("Mule sweep for org {} scored {} beneficiaries", orgId, scored.size());
        return scored;
    }

    /** Marks an account as a confirmed mule — from an analyst, a receiving bank, or the consortium. */
    @Transactional
    public BeneficiaryRisk confirm(UUID orgId, String beneficiaryId, String source) {
        BeneficiaryRisk risk = beneficiaryRiskRepository
                .findByOrganizationIdAndBeneficiaryId(orgId, beneficiaryId)
                .orElseGet(() -> BeneficiaryRisk.builder()
                        .organizationId(orgId)
                        .beneficiaryId(beneficiaryId)
                        .fanIn(0)
                        .creditCount(0)
                        .totalCredited(BigDecimal.ZERO)
                        .smallCreditRatio(0)
                        .activityWindowDays(0)
                        .distinctCountries(0)
                        .reasons(new ArrayList<>())
                        .firstSeen(Instant.now())
                        .build());

        List<String> reasons = new ArrayList<>(risk.getReasons() == null ? List.of() : risk.getReasons());
        reasons.add("Confirmed mule — source: " + source);
        risk.setReasons(reasons);
        risk.setExternallyConfirmed(true);
        risk.setScore(100);
        risk.setBand(MuleBand.CONFIRMED);
        risk.setLastSeen(Instant.now());
        return beneficiaryRiskRepository.save(risk);
    }

    /** The payers who sent money to a flagged account — the victim list for outreach and recall. */
    @Transactional(readOnly = true)
    public List<String> payersOf(UUID orgId, String beneficiaryId) {
        return analytics.findPayers(orgId, beneficiaryId,
                Instant.now().minus(OBSERVATION_DAYS, ChronoUnit.DAYS));
    }

    private int scoreFanIn(long fanIn, List<String> reasons) {
        if (fanIn >= 40) {
            reasons.add(fanIn + " unrelated payers in 30 days");
            return 40;
        }
        if (fanIn >= 20) {
            reasons.add(fanIn + " unrelated payers in 30 days");
            return 32;
        }
        if (fanIn >= 10) {
            reasons.add(fanIn + " unrelated payers in 30 days");
            return 22;
        }
        if (fanIn >= 5) {
            reasons.add(fanIn + " unrelated payers in 30 days");
            return 12;
        }
        return 0;
    }

    private int scoreSmurfing(double smallRatio, long credits, List<String> reasons) {
        if (credits < 5) return 0;
        if (smallRatio >= 0.8) {
            reasons.add(Math.round(smallRatio * 100) + "% of credits are below the small-value threshold");
            return 20;
        }
        if (smallRatio >= 0.5) {
            reasons.add(Math.round(smallRatio * 100) + "% of credits are below the small-value threshold");
            return 12;
        }
        return 0;
    }

    private int scoreBurst(long credits, int windowDays, List<String> reasons) {
        if (credits >= 20 && windowDays <= 7) {
            reasons.add(credits + " credits inside a " + windowDays + "-day window");
            return 15;
        }
        if (credits >= 10 && windowDays <= 14) {
            reasons.add(credits + " credits inside a " + windowDays + "-day window");
            return 9;
        }
        return 0;
    }

    private int scoreDispersion(long countries, List<String> reasons) {
        if (countries >= 5) {
            reasons.add("Payers span " + countries + " countries");
            return 15;
        }
        if (countries >= 3) {
            reasons.add("Payers span " + countries + " countries");
            return 8;
        }
        return 0;
    }

    private int scoreVelocity(long credits, int windowDays, List<String> reasons) {
        if (windowDays <= 0) return 0;
        double perDay = (double) credits / windowDays;
        if (perDay >= 10) {
            reasons.add(String.format("%.1f credits per active day", perDay));
            return 10;
        }
        if (perDay >= 4) {
            reasons.add(String.format("%.1f credits per active day", perDay));
            return 5;
        }
        return 0;
    }

    private int activityWindowDays(UUID orgId, String beneficiaryId) {
        List<Object[]> window = analytics.activityWindow(orgId, beneficiaryId);
        if (window.isEmpty() || window.get(0)[0] == null || window.get(0)[1] == null) {
            return 0;
        }
        Instant first = (Instant) window.get(0)[0];
        Instant last = (Instant) window.get(0)[1];
        long days = Duration.between(first, last).toDays();
        return (int) Math.max(1, days);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
