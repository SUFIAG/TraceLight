package com.sentinel.demo.application.service;

import com.sentinel.alert.adapter.out.persistence.AlertRepository;
import com.sentinel.alert.domain.model.AlertSeverity;
import com.sentinel.alert.domain.model.FraudAlert;
import com.sentinel.cases.application.dto.CreateCaseRequest;
import com.sentinel.cases.adapter.out.persistence.FraudCaseRepository;
import com.sentinel.cases.application.service.CaseService;
import com.sentinel.common.config.CacheConfig;
import com.sentinel.demo.application.dto.SeedResponse;
import com.sentinel.fraud.application.service.FraudAnalysisService;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.application.dto.IngestTransactionRequest;
import com.sentinel.transaction.application.dto.TransactionResponse;
import com.sentinel.transaction.application.service.TransactionService;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates a realistic demo dataset by driving the real ingestion and fraud
 * analysis pipeline. Nothing is inserted directly into alert or risk tables, so
 * everything a demo shows — risk scores, alerts, explanations, audit entries —
 * is produced by the same code paths that serve production traffic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataService {

    /** Fixed seed so a demo looks the same every time it is run. */
    private static final long RANDOM_SEED = 20261003L;

    /** Above this many existing transactions, seeding is skipped unless forced. */
    private static final int ALREADY_SEEDED_THRESHOLD = 25;

    private static final int DAYS_OF_HISTORY = 14;
    private static final int TRANSACTIONS_TO_CREATE = 220;
    private static final int MAX_CASES_TO_CREATE = 6;

    private static final String[] MERCHANTS = {
            "AMZN_RETAIL_01", "UBER_RIDES_02", "NETFLIX_SUB_03", "SHELL_FUEL_04",
            "TESCO_GROCERY_05", "APPLE_STORE_06", "BOOKING_TRAVEL_07", "STEAM_GAMES_08",
            "IKEA_HOME_09", "ZARA_FASHION_10"
    };

    /** Merchant identifiers that BlacklistedMerchantRule recognises. */
    private static final String[] BLACKLISTED_MERCHANTS = {
            "MERCHANT_FRAUD_001", "MERCHANT_FRAUD_002", "MERCHANT_SUSPICIOUS_003",
            "MERCHANT_BLOCKED_004", "MERCHANT_RISK_005"
    };

    private static final String[] FOREIGN_COUNTRIES = { "NG", "RU", "VN", "BR", "UA", "ID" };

    private record Customer(String userId, String homeCountry, String currency, String primaryDevice) { }

    private static final List<Customer> CUSTOMERS = List.of(
            new Customer("CUST_10045", "GB", "GBP", "dev_gb_4471"),
            new Customer("CUST_10046", "GB", "GBP", "dev_gb_8820"),
            new Customer("CUST_10047", "US", "USD", "dev_us_1193"),
            new Customer("CUST_10048", "US", "USD", "dev_us_6627"),
            new Customer("CUST_10049", "DE", "EUR", "dev_de_3310"),
            new Customer("CUST_10050", "FR", "EUR", "dev_fr_7742"),
            new Customer("CUST_10051", "AE", "AED", "dev_ae_2208"),
            new Customer("CUST_10052", "SA", "SAR", "dev_sa_9915"),
            new Customer("CUST_10053", "NL", "EUR", "dev_nl_5503"),
            new Customer("CUST_10054", "ES", "EUR", "dev_es_1176"),
            new Customer("CUST_10055", "US", "USD", "dev_us_4408"),
            new Customer("CUST_10056", "GB", "GBP", "dev_gb_6691")
    );

    private final TransactionService transactionService;
    private final FraudAnalysisService fraudAnalysisService;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final CaseService caseService;
    private final FraudCaseRepository fraudCaseRepository;

    // DashboardService caches its counters for a minute. Without this eviction the
    // dashboard would keep reporting zero for up to 60s after a seed, which is
    // exactly the moment someone is watching it.
    //
    // Deliberately NOT @Transactional. Seeding is a generator, not an atomic unit:
    // it already catches and continues past individual failures. Wrapping it in one
    // transaction broke that. Every inner call (ingestTransaction, createFromAlert)
    // is itself @Transactional with the default REQUIRED propagation, so it joined
    // the outer transaction; when one threw — CASE_EXISTS on a re-run, for instance —
    // Spring marked the shared transaction rollback-only, the catch block swallowed
    // the exception, and the outer commit then failed with UnexpectedRollbackException.
    // The endpoint returned 500 and discarded all 220 rows it had just created.
    @CacheEvict(value = CacheConfig.DASHBOARD, key = "#organizationId")
    public SeedResponse seed(UUID organizationId, UUID userId, boolean force) {
        long existing = transactionRepository
                .findByOrganizationId(organizationId, PageRequest.of(0, 1))
                .getTotalElements();

        if (existing > ALREADY_SEEDED_THRESHOLD && !force) {
            return SeedResponse.builder()
                    .skipped(true)
                    .message("Organization already holds " + existing
                            + " transactions. Pass force=true to seed anyway.")
                    .build();
        }

        Random random = new Random(RANDOM_SEED);
        String runId = UUID.randomUUID().toString().substring(0, 8);

        List<IngestTransactionRequest> plan = new ArrayList<>();
        for (int i = 0; i < TRANSACTIONS_TO_CREATE; i++) {
            plan.add(buildTransaction(random, runId, i));
        }
        // Scripted scenarios on top of the random population, so a demo always
        // contains at least one clean example of each headline detection.
        plan.addAll(buildCardTestingBurst(runId));
        plan.addAll(buildImpossibleTravelPair(runId));
        plan.addAll(buildAccountTakeoverSequence(runId));

        // Velocity, geographic-anomaly and impossible-travel all read the history
        // that precedes a transaction. Ingesting and scoring in timestamp order is
        // therefore not cosmetic: out of order, a burst looks like six unrelated
        // transactions and none of those rules can fire.
        plan.sort(Comparator.comparing(IngestTransactionRequest::getTimestamp));

        List<UUID> createdIds = new ArrayList<>();
        for (IngestTransactionRequest request : plan) {
            try {
                TransactionResponse created = transactionService.ingestTransaction(
                        request, organizationId, UUID.randomUUID().toString());
                createdIds.add(created.getId());
            } catch (Exception e) {
                log.debug("Skipped demo transaction {}: {}", request.getTransactionExternalId(), e.getMessage());
            }
        }

        for (UUID transactionId : createdIds) {
            try {
                fraudAnalysisService.analyzeTransaction(transactionId);
            } catch (Exception e) {
                log.warn("Demo analysis failed for {}: {}", transactionId, e.getMessage());
            }
        }

        int flagged = (int) transactionRepository
                .findByOrganizationIdAndStatus(organizationId, TransactionStatus.FLAGGED, PageRequest.of(0, 1))
                .getTotalElements();

        var alertPage = alertRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId, PageRequest.of(0, 100));
        List<FraudAlert> alerts = alertPage.getContent();
        // The page holds at most 100; report the true total rather than the page size.
        int alertCount = (int) alertPage.getTotalElements();

        int casesCreated = createCasesForTopAlerts(organizationId, userId, alerts);

        log.info("Demo seed complete for org {}: {} transactions, {} flagged, {} alerts, {} cases",
                organizationId, createdIds.size(), flagged, alertCount, casesCreated);

        return SeedResponse.builder()
                .transactionsCreated(createdIds.size())
                .transactionsFlagged(flagged)
                .alertsCreated(alertCount)
                .casesCreated(casesCreated)
                .skipped(false)
                .message("Demo dataset generated through the live fraud pipeline.")
                .build();
    }

    /**
     * Builds one transaction. Roughly 70% are ordinary customer activity and 30%
     * carry a fraud signature that the rule engine is designed to catch.
     */
    private IngestTransactionRequest buildTransaction(Random random, String runId, int index) {
        Customer customer = CUSTOMERS.get(random.nextInt(CUSTOMERS.size()));
        int roll = random.nextInt(100);

        String merchant;
        BigDecimal amount;
        String country = customer.homeCountry();
        String device = customer.primaryDevice();
        Instant timestamp = randomTimestampWithinHistory(random);

        if (roll < 70) {
            // Ordinary spending: modest amount, home country, known device, daytime.
            merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            amount = money(random, 5, 400);
            timestamp = shiftToBusinessHours(timestamp);
        } else if (roll < 80) {
            // High-value purchase from a new country on an unknown device.
            merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            amount = money(random, 5200, 9800);
            country = FOREIGN_COUNTRIES[random.nextInt(FOREIGN_COUNTRIES.length)];
            device = "dev_unknown_" + random.nextInt(9999);
        } else if (roll < 88) {
            // Blacklisted merchant, mid-value, off-hours.
            merchant = BLACKLISTED_MERCHANTS[random.nextInt(BLACKLISTED_MERCHANTS.length)];
            amount = money(random, 300, 2400);
            timestamp = shiftToOffHours(timestamp);
        } else if (roll < 94) {
            // Critical: very large amount, foreign country, off-hours, new device.
            merchant = BLACKLISTED_MERCHANTS[random.nextInt(BLACKLISTED_MERCHANTS.length)];
            amount = money(random, 10500, 48000);
            country = FOREIGN_COUNTRIES[random.nextInt(FOREIGN_COUNTRIES.length)];
            device = "dev_unknown_" + random.nextInt(9999);
            timestamp = shiftToOffHours(timestamp);
        } else {
            // Card testing: a string of very small amounts on an unknown device.
            merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            amount = money(random, 1, 4);
            device = "dev_unknown_" + random.nextInt(9999);
            timestamp = shiftToOffHours(timestamp);
        }

        return IngestTransactionRequest.builder()
                .transactionExternalId("TXN-" + runId + "-" + String.format("%05d", index))
                .userId(customer.userId())
                .merchantId(merchant)
                .amount(amount)
                .currency(customer.currency())
                .country(country)
                .deviceId(device)
                .ipAddress(randomIp(random))
                .timestamp(timestamp)
                .build();
    }

    /**
     * Card testing: eight micro-authorisations on one card inside six minutes from a
     * device never seen before. Fires RAPID_TRANSACTION and the micro-amount band of
     * the amount dimension.
     */
    private List<IngestTransactionRequest> buildCardTestingBurst(String runId) {
        Customer victim = CUSTOMERS.get(2);
        Instant start = Instant.now().minus(Duration.ofHours(5));
        String device = "dev_bot_" + runId;

        List<IngestTransactionRequest> burst = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            burst.add(IngestTransactionRequest.builder()
                    .transactionExternalId("TXN-" + runId + "-CARDTEST-" + i)
                    .userId(victim.userId())
                    .merchantId(MERCHANTS[i % MERCHANTS.length])
                    .amount(new BigDecimal("1." + (10 + i)))
                    .currency(victim.currency())
                    .country(victim.homeCountry())
                    .deviceId(device)
                    .ipAddress("185.220.101." + (10 + i))
                    .timestamp(start.plus(Duration.ofSeconds(45L * i)))
                    .build());
        }
        return burst;
    }

    /**
     * Impossible travel: the same card in London and then Jakarta forty minutes
     * later. No legitimate explanation exists, which makes it the cleanest single
     * detection to show an evaluator.
     */
    private List<IngestTransactionRequest> buildImpossibleTravelPair(String runId) {
        Customer victim = CUSTOMERS.get(0);
        Instant first = Instant.now().minus(Duration.ofHours(9));

        return List.of(
                IngestTransactionRequest.builder()
                        .transactionExternalId("TXN-" + runId + "-TRAVEL-1")
                        .userId(victim.userId())
                        .merchantId("TESCO_GROCERY_05")
                        .amount(new BigDecimal("64.20"))
                        .currency(victim.currency())
                        .country("GB")
                        .deviceId(victim.primaryDevice())
                        .ipAddress("81.2.69.142")
                        .timestamp(first)
                        .build(),
                IngestTransactionRequest.builder()
                        .transactionExternalId("TXN-" + runId + "-TRAVEL-2")
                        .userId(victim.userId())
                        .merchantId("MERCHANT_SUSPICIOUS_003")
                        .amount(new BigDecimal("4310.00"))
                        .currency(victim.currency())
                        .country("ID")
                        .deviceId("dev_unknown_" + runId)
                        .ipAddress("103.28.148.21")
                        .timestamp(first.plus(Duration.ofMinutes(40)))
                        .build());
    }

    /**
     * Account takeover: a quiet low-value customer whose card is suddenly used for a
     * large purchase from a new device, a new country and at 03:00. Exercises
     * AMOUNT_DEVIATION, which is the rule that catches fraud a fixed threshold misses.
     */
    private List<IngestTransactionRequest> buildAccountTakeoverSequence(String runId) {
        Customer victim = CUSTOMERS.get(7);
        Instant base = Instant.now().minus(Duration.ofDays(3));

        List<IngestTransactionRequest> sequence = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sequence.add(IngestTransactionRequest.builder()
                    .transactionExternalId("TXN-" + runId + "-ATO-BASE-" + i)
                    .userId(victim.userId())
                    .merchantId(MERCHANTS[i % MERCHANTS.length])
                    .amount(new BigDecimal("22." + (10 + i)))
                    .currency(victim.currency())
                    .country(victim.homeCountry())
                    .deviceId(victim.primaryDevice())
                    .ipAddress("91.73.14." + (20 + i))
                    .timestamp(base.plus(Duration.ofHours(6L * i)))
                    .build());
        }

        sequence.add(IngestTransactionRequest.builder()
                .transactionExternalId("TXN-" + runId + "-ATO-HIT")
                .userId(victim.userId())
                .merchantId("MERCHANT_FRAUD_001")
                .amount(new BigDecimal("12750.00"))
                .currency(victim.currency())
                .country("RU")
                .deviceId("dev_takeover_" + runId)
                .ipAddress("45.133.1.88")
                .timestamp(alignHour(Instant.now().minus(Duration.ofDays(1)), 3))
                .build());

        return sequence;
    }

    private int createCasesForTopAlerts(UUID organizationId, UUID userId, List<FraudAlert> alerts) {
        List<FraudAlert> priority = alerts.stream()
                .filter(a -> a.getSeverity() == AlertSeverity.CRITICAL || a.getSeverity() == AlertSeverity.HIGH)
                .limit(MAX_CASES_TO_CREATE)
                .toList();

        int created = 0;
        for (FraudAlert alert : priority) {
            // Checked before calling rather than relying on catching CASE_EXISTS, so a
            // re-run costs no failed transactions.
            if (fraudCaseRepository.existsByAlertId(alert.getId())) {
                continue;
            }
            try {
                caseService.createFromAlert(organizationId, userId, CreateCaseRequest.builder()
                        .alertId(alert.getId())
                        .title(alert.getSeverity().name() + " risk review — alert "
                                + alert.getId().toString().substring(0, 8))
                        .build());
                created++;
            } catch (Exception e) {
                log.debug("Skipped demo case for alert {}: {}", alert.getId(), e.getMessage());
            }
        }
        return created;
    }

    private Instant randomTimestampWithinHistory(Random random) {
        long secondsOfHistory = Duration.ofDays(DAYS_OF_HISTORY).toSeconds();
        // Keep everything at least an hour in the past so no timestamp is rejected as future-dated.
        return Instant.now()
                .minusSeconds(3600)
                .minusSeconds((long) (random.nextDouble() * secondsOfHistory));
    }

    private Instant shiftToBusinessHours(Instant instant) {
        return alignHour(instant, 9 + (int) (instant.getEpochSecond() % 10));
    }

    private Instant shiftToOffHours(Instant instant) {
        return alignHour(instant, (int) Math.abs(instant.getEpochSecond() % 5));
    }

    /** Moves an instant to a given UTC hour on the same day, never into the future. */
    private Instant alignHour(Instant instant, int hour) {
        Instant aligned = instant.truncatedTo(ChronoUnit.DAYS).plus(hour, ChronoUnit.HOURS);
        return aligned.isAfter(Instant.now().minusSeconds(600))
                ? aligned.minus(1, ChronoUnit.DAYS)
                : aligned;
    }

    private BigDecimal money(Random random, int minInclusive, int maxInclusive) {
        int units = minInclusive + random.nextInt(Math.max(1, maxInclusive - minInclusive + 1));
        int cents = random.nextInt(100);
        return BigDecimal.valueOf(units).add(BigDecimal.valueOf(cents, 2));
    }

    private String randomIp(Random random) {
        return (1 + random.nextInt(223)) + "." + random.nextInt(256)
                + "." + random.nextInt(256) + "." + (1 + random.nextInt(254));
    }
}
