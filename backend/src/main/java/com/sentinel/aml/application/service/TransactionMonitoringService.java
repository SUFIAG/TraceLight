package com.sentinel.aml.application.service;

import com.sentinel.aml.adapter.out.persistence.AmlAnalyticsRepository;
import com.sentinel.aml.adapter.out.persistence.AmlFindingRepository;
import com.sentinel.aml.domain.model.AmlFinding;
import com.sentinel.aml.domain.model.AmlTypology;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Behavioural AML monitoring.
 *
 * <p>Rule-based thresholds are what every incumbent sells and what every
 * examiner expects, so they are here — but each detector is written around
 * the <em>reason</em> the pattern is suspicious rather than around a number.
 * Structuring is not "payments near 10,000"; it is repeated payments that sit
 * just under a threshold when nothing about the underlying activity would
 * naturally cluster there.
 *
 * <p>Findings are deduplicated per subject, typology, and window so persistent
 * behaviour strengthens one finding instead of generating a new alert daily.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionMonitoringService {

    /** The reporting threshold most regimes set for cash and wire reporting. */
    private static final BigDecimal REPORTING_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal STRUCTURING_FLOOR = new BigDecimal("8000");
    private static final int STRUCTURING_MIN_COUNT = 3;

    /** Share of inbound value that must leave again to count as pass-through. */
    private static final double PASSTHROUGH_RATIO = 0.85;
    private static final int DORMANCY_DAYS = 180;

    /** FATF increased-monitoring and call-for-action jurisdictions. */
    private static final Set<String> HIGH_RISK_COUNTRIES =
            Set.of("IR", "KP", "MM", "SY", "YE", "AF", "SS", "HT", "CD");

    private final AmlAnalyticsRepository analytics;
    private final AmlFindingRepository findingRepository;

    /** Monitors one customer across every typology. */
    @Transactional
    public List<AmlFinding> monitor(UUID orgId, String subjectId, int lookbackDays) {
        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<Transaction> outbound = analytics.outbound(orgId, subjectId, since);
        List<Transaction> inbound = analytics.inbound(orgId, subjectId, since);

        List<AmlFinding> findings = new ArrayList<>();
        addIfPresent(findings, detectStructuring(orgId, subjectId, outbound));
        addIfPresent(findings, detectRapidMovement(orgId, subjectId, inbound, outbound));
        addIfPresent(findings, detectRoundTripping(orgId, subjectId, outbound, since));
        addIfPresent(findings, detectHighRiskJurisdiction(orgId, subjectId, outbound, inbound));
        addIfPresent(findings, detectDormantReactivation(orgId, subjectId, outbound, inbound, since));
        addIfPresent(findings, detectThirdPartyFunding(orgId, subjectId, inbound));
        return findings;
    }

    /** Sweeps every active customer. The nightly batch, runnable on demand. */
    @Transactional
    public List<AmlFinding> sweep(UUID orgId, int lookbackDays, int maxSubjects) {
        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<String> subjects = analytics.activeSubjects(orgId, since, PageRequest.of(0, maxSubjects));
        List<AmlFinding> all = new ArrayList<>();
        for (String subject : subjects) {
            all.addAll(monitor(orgId, subject, lookbackDays));
        }
        log.info("AML sweep for org {}: {} subjects, {} findings", orgId, subjects.size(), all.size());
        return all;
    }

    /* ---------- Detectors ---------- */

    private Optional<AmlFinding> detectStructuring(UUID orgId, String subjectId, List<Transaction> outbound) {
        List<Transaction> nearThreshold = outbound.stream()
                .filter(t -> t.getAmount().compareTo(STRUCTURING_FLOOR) >= 0
                        && t.getAmount().compareTo(REPORTING_THRESHOLD) < 0)
                .toList();

        if (nearThreshold.size() < STRUCTURING_MIN_COUNT) return Optional.empty();

        BigDecimal total = sum(nearThreshold);
        // Several payments that each stop just short of the threshold, adding to
        // well over it, is the whole definition of the offence.
        if (total.compareTo(REPORTING_THRESHOLD) <= 0) return Optional.empty();

        List<String> evidence = new ArrayList<>();
        evidence.add(nearThreshold.size() + " payments between " + STRUCTURING_FLOOR + " and " + REPORTING_THRESHOLD);
        evidence.add("Aggregate value " + total + ", well above the reporting threshold");
        nearThreshold.stream().limit(5).forEach(t ->
                evidence.add("Payment of " + t.getAmount() + " to " + t.getMerchantId() + " on " + t.getTimestamp()));

        int score = Math.min(100, 45 + nearThreshold.size() * 8);
        return Optional.of(upsert(orgId, subjectId, AmlTypology.STRUCTURING, score,
                nearThreshold.size() + " payments deliberately kept below the reporting threshold, totalling " + total,
                evidence, nearThreshold, total));
    }

    private Optional<AmlFinding> detectRapidMovement(UUID orgId, String subjectId,
                                                     List<Transaction> inbound, List<Transaction> outbound) {
        if (inbound.isEmpty() || outbound.isEmpty()) return Optional.empty();

        BigDecimal inboundTotal = sum(inbound);
        BigDecimal outboundTotal = sum(outbound);
        if (inboundTotal.signum() == 0) return Optional.empty();

        double ratio = outboundTotal.doubleValue() / inboundTotal.doubleValue();
        if (ratio < PASSTHROUGH_RATIO) return Optional.empty();

        // How long value actually rests in the account. Pass-through accounts
        // hold funds for hours; a genuine operating account holds them for days.
        long medianRestHours = medianRestHours(inbound, outbound);
        if (medianRestHours > 48) return Optional.empty();

        List<String> evidence = List.of(
                "Inbound " + inboundTotal + " against outbound " + outboundTotal,
                String.format("%.0f%% of credits leave the account again", ratio * 100),
                "Median resting time " + medianRestHours + " hours");

        int score = Math.min(100, 50 + (int) ((1.0 - medianRestHours / 48.0) * 35));
        return Optional.of(upsert(orgId, subjectId, AmlTypology.RAPID_MOVEMENT, score,
                "Funds pass straight through the account with a median resting time of "
                        + medianRestHours + " hours",
                evidence, outbound, outboundTotal));
    }

    private Optional<AmlFinding> detectRoundTripping(UUID orgId, String subjectId,
                                                     List<Transaction> outbound, Instant since) {
        Set<String> counterparties = new HashSet<>();
        outbound.forEach(t -> counterparties.add(t.getMerchantId()));

        List<String> returned = new ArrayList<>();
        for (String counterparty : counterparties) {
            if (analytics.countFlow(orgId, counterparty, subjectId, since) > 0) {
                returned.add(counterparty);
            }
        }
        if (returned.isEmpty()) return Optional.empty();

        List<String> evidence = new ArrayList<>();
        evidence.add("Value returned from " + returned.size() + " counterparties it was sent to");
        returned.stream().limit(5).forEach(c -> evidence.add("Two-way flow with " + c));

        int score = Math.min(100, 40 + returned.size() * 12);
        return Optional.of(upsert(orgId, subjectId, AmlTypology.ROUND_TRIPPING, score,
                "Funds sent to " + returned.size() + " counterparties returned to this account",
                evidence, outbound, sum(outbound)));
    }

    private Optional<AmlFinding> detectHighRiskJurisdiction(UUID orgId, String subjectId,
                                                            List<Transaction> outbound, List<Transaction> inbound) {
        List<Transaction> exposed = new ArrayList<>();
        outbound.stream().filter(t -> t.getCountry() != null && HIGH_RISK_COUNTRIES.contains(t.getCountry()))
                .forEach(exposed::add);
        inbound.stream().filter(t -> t.getCountry() != null && HIGH_RISK_COUNTRIES.contains(t.getCountry()))
                .forEach(exposed::add);

        if (exposed.isEmpty()) return Optional.empty();

        BigDecimal total = sum(exposed);
        Set<String> countries = new TreeSet<>();
        exposed.forEach(t -> countries.add(t.getCountry()));

        List<String> evidence = List.of(
                exposed.size() + " payments involving " + String.join(", ", countries),
                "Aggregate exposure " + total);

        int score = Math.min(100, 45 + exposed.size() * 5 + countries.size() * 8);
        return Optional.of(upsert(orgId, subjectId, AmlTypology.HIGH_RISK_JURISDICTION, score,
                "Exposure to FATF-listed jurisdictions: " + String.join(", ", countries),
                evidence, exposed, total));
    }

    private Optional<AmlFinding> detectDormantReactivation(UUID orgId, String subjectId,
                                                           List<Transaction> outbound, List<Transaction> inbound,
                                                           Instant since) {
        List<Transaction> recent = new ArrayList<>(outbound);
        recent.addAll(inbound);
        if (recent.isEmpty()) return Optional.empty();

        recent.sort(Comparator.comparing(Transaction::getTimestamp));
        Transaction first = recent.get(0);

        List<Transaction> before = analytics.activityBefore(orgId, subjectId, since, PageRequest.of(0, 1));
        if (before.isEmpty()) return Optional.empty();

        long dormantDays = Duration.between(before.get(0).getTimestamp(), first.getTimestamp()).toDays();
        if (dormantDays < DORMANCY_DAYS) return Optional.empty();

        BigDecimal total = sum(recent);
        if (total.compareTo(REPORTING_THRESHOLD) < 0) return Optional.empty();

        List<String> evidence = List.of(
                "Account was dormant for " + dormantDays + " days",
                "Reactivated with " + recent.size() + " payments totalling " + total);

        int score = Math.min(100, 40 + (int) Math.min(30, dormantDays / 30) + 10);
        return Optional.of(upsert(orgId, subjectId, AmlTypology.DORMANT_REACTIVATION, score,
                "Account dormant for " + dormantDays + " days reactivated with " + total + " of activity",
                evidence, recent, total));
    }

    private Optional<AmlFinding> detectThirdPartyFunding(UUID orgId, String subjectId, List<Transaction> inbound) {
        Set<String> payers = new HashSet<>();
        inbound.forEach(t -> payers.add(t.getUserId()));
        if (payers.size() < 8) return Optional.empty();

        BigDecimal total = sum(inbound);
        List<String> evidence = List.of(
                payers.size() + " distinct funding parties",
                inbound.size() + " credits totalling " + total);

        int score = Math.min(100, 35 + payers.size() * 3);
        return Optional.of(upsert(orgId, subjectId, AmlTypology.THIRD_PARTY_FUNDING, score,
                "Account funded by " + payers.size() + " unrelated third parties",
                evidence, inbound, total));
    }

    /* ---------- Shared ---------- */

    /**
     * Upserts on (subject, typology, 7-day window). A pattern that persists
     * raises the score on the existing finding rather than creating another
     * one for the same behaviour.
     */
    private AmlFinding upsert(UUID orgId, String subjectId, AmlTypology typology, int score,
                              String narrative, List<String> evidence,
                              List<Transaction> transactions, BigDecimal total) {
        Instant dedupeFloor = Instant.now().minus(7, ChronoUnit.DAYS);
        AmlFinding finding = findingRepository.findRecent(orgId, subjectId, typology, dedupeFloor)
                .orElseGet(() -> AmlFinding.builder()
                        .organizationId(orgId)
                        .subjectId(subjectId)
                        .typology(typology)
                        .status("OPEN")
                        .build());

        finding.setScore(Math.max(finding.getScore(), score));
        finding.setNarrative(narrative);
        finding.setEvidence(evidence);
        finding.setTransactionIds(transactions.stream().map(t -> t.getId().toString()).limit(100).toList());
        finding.setTotalValue(total);
        finding.setWindowStart(transactions.isEmpty() ? Instant.now()
                : transactions.stream().map(Transaction::getTimestamp).min(Instant::compareTo).orElse(Instant.now()));
        finding.setWindowEnd(transactions.isEmpty() ? Instant.now()
                : transactions.stream().map(Transaction::getTimestamp).max(Instant::compareTo).orElse(Instant.now()));
        return findingRepository.save(finding);
    }

    private long medianRestHours(List<Transaction> inbound, List<Transaction> outbound) {
        List<Long> gaps = new ArrayList<>();
        for (Transaction credit : inbound) {
            outbound.stream()
                    .filter(debit -> debit.getTimestamp().isAfter(credit.getTimestamp()))
                    .findFirst()
                    .ifPresent(debit -> gaps.add(Duration.between(credit.getTimestamp(), debit.getTimestamp()).toHours()));
        }
        if (gaps.isEmpty()) return Long.MAX_VALUE;
        Collections.sort(gaps);
        return gaps.get(gaps.size() / 2);
    }

    private BigDecimal sum(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addIfPresent(List<AmlFinding> findings, Optional<AmlFinding> finding) {
        finding.ifPresent(findings::add);
    }
}
