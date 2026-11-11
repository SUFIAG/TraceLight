package com.sentinel.behavioral.velocity.application.service;

import com.sentinel.behavioral.velocity.application.dto.VelocityResponse;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sliding-window velocity analysis used to detect card-testing and rapid
 * fraud bursts. Windows are computed on demand against indexed transaction
 * data (organization_id, user_id, timestamp) to keep write amplification at zero.
 *
 * <p>Two properties matter here and both were wrong before:
 * <ul>
 *   <li>Windows are anchored on the <em>transaction's own</em> timestamp, not on
 *       wall-clock time. Scoring a CSV-loaded or replayed transaction against
 *       "the last 10 minutes of real time" always returns zero, which silently
 *       disabled velocity detection for every batch ingestion path.</li>
 *   <li>Every query is scoped by organization. {@code userId} is a tenant-supplied
 *       customer identifier and is not globally unique.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityAnalyzer {

    private static final Duration WINDOW_10_MIN = Duration.ofMinutes(10);
    private static final Duration WINDOW_1_HOUR = Duration.ofHours(1);
    private static final Duration WINDOW_24_HOURS = Duration.ofHours(24);

    private final TransactionRepository transactionRepository;

    /**
     * Velocity risk contribution (0-100) for the customer behind this transaction,
     * measured over the windows ending at the transaction's own timestamp.
     */
    public int calculateVelocityRisk(Transaction txn) {
        Instant at = txn.getTimestamp() != null ? txn.getTimestamp() : Instant.now();
        long count10Min = count(txn.getOrganizationId(), txn.getUserId(), at, WINDOW_10_MIN);
        long count1Hour = count(txn.getOrganizationId(), txn.getUserId(), at, WINDOW_1_HOUR);
        long count24Hours = count(txn.getOrganizationId(), txn.getUserId(), at, WINDOW_24_HOURS);
        return riskFor(count10Min, count1Hour, count24Hours);
    }

    @Transactional(readOnly = true)
    public VelocityResponse analyze(UUID organizationId, String userId) {
        Instant now = Instant.now();
        Instant since10Min = now.minus(WINDOW_10_MIN);
        Instant since1Hour = now.minus(WINDOW_1_HOUR);
        Instant since24Hours = now.minus(WINDOW_24_HOURS);

        long count10Min = transactionRepository.countInWindowByUserId(organizationId, userId, since10Min, now);
        long count1Hour = transactionRepository.countInWindowByUserId(organizationId, userId, since1Hour, now);
        long count24Hours = transactionRepository.countInWindowByUserId(organizationId, userId, since24Hours, now);

        return VelocityResponse.builder()
                .userId(userId)
                .count10Min(count10Min)
                .count1Hour(count1Hour)
                .count24Hours(count24Hours)
                .amount10Min(transactionRepository.sumAmountInWindowByUserId(organizationId, userId, since10Min, now))
                .amount1Hour(transactionRepository.sumAmountInWindowByUserId(organizationId, userId, since1Hour, now))
                .amount24Hours(transactionRepository.sumAmountInWindowByUserId(organizationId, userId, since24Hours, now))
                .distinctMerchants1Hour(transactionRepository.countDistinctMerchantsInWindow(organizationId, userId, since1Hour, now))
                .distinctCountries1Hour(transactionRepository.countDistinctCountriesInWindow(organizationId, userId, since1Hour, now))
                .velocityRisk(riskFor(count10Min, count1Hour, count24Hours))
                .rapidActivityDetected(count10Min >= 5 || count1Hour >= 10)
                .build();
    }

    private long count(UUID organizationId, String userId, Instant endOfWindow, Duration window) {
        return transactionRepository.countInWindowByUserId(
                organizationId, userId, endOfWindow.minus(window), endOfWindow);
    }

    /**
     * Counts include the transaction being scored, so the thresholds are stated
     * as "this transaction plus N others".
     */
    private int riskFor(long count10Min, long count1Hour, long count24Hours) {
        if (count10Min >= 5) return 95;    // card-testing burst
        if (count1Hour >= 10) return 95;   // sustained rapid fraud
        if (count10Min >= 3) return 80;
        if (count1Hour >= 5) return 70;
        if (count1Hour >= 3) return 45;
        if (count24Hours >= 20) return 40; // grinding low-and-slow activity
        return 5;
    }
}
