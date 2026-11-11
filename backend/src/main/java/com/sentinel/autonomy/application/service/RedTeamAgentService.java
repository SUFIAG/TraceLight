package com.sentinel.autonomy.application.service;

import com.sentinel.autonomy.adapter.out.persistence.AgentRunRepository;
import com.sentinel.autonomy.adapter.out.persistence.AttackSimulationRepository;
import com.sentinel.autonomy.domain.model.AgentRun;
import com.sentinel.autonomy.domain.model.AgentVerdict;
import com.sentinel.autonomy.domain.model.AttackSimulation;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.fraud.rules.domain.service.RuleEngine;
import com.sentinel.transaction.domain.model.Transaction;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * The adversarial agent: attacks the institution's own controls.
 *
 * <p>Detection coverage is usually measured against fraud that was caught,
 * which is circular — it says nothing about the attack shapes that walk
 * straight through. This agent generates each known attack pattern as
 * synthetic traffic, runs it through the live rule set, and reports what
 * would have been missed.
 *
 * <p>Nothing is persisted to the transaction ledger. The synthetic
 * transactions exist only in memory for the duration of the evaluation, so a
 * red-team run can never contaminate real analytics or a model's training
 * data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedTeamAgentService {

    private static final String AGENT_NAME = "RED_TEAM";
    /** A triggered rule at or above this contribution counts as a catch. */
    private static final int DETECTION_THRESHOLD = 50;

    private final RuleEngine ruleEngine;
    private final AttackSimulationRepository simulationRepository;
    private final AgentRunRepository agentRunRepository;

    /** Runs every scenario in the library. */
    @Transactional
    public List<AttackSimulation> runAll(UUID orgId) {
        List<AttackSimulation> results = new ArrayList<>();
        for (Scenario scenario : library()) {
            results.add(run(orgId, scenario));
        }
        long failed = results.stream().filter(r -> !"PASS".equals(r.getOutcome())).count();
        log.info("Red team run for org {}: {} scenarios, {} with gaps", orgId, results.size(), failed);
        return results;
    }

    @Transactional
    public AttackSimulation run(UUID orgId, String scenarioKey) {
        Scenario scenario = library().stream()
                .filter(s -> s.key().equals(scenarioKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + scenarioKey));
        return run(orgId, scenario);
    }

    private AttackSimulation run(UUID orgId, Scenario scenario) {
        long start = System.currentTimeMillis();
        List<Transaction> synthetic = scenario.generator().get();

        int detected = 0;
        int peak = 0;
        Set<String> firedRules = new TreeSet<>();

        for (Transaction transaction : synthetic) {
            transaction.setOrganizationId(orgId);
            List<RuleResult> results = ruleEngine.evaluate(transaction);
            int strongest = results.stream()
                    .filter(RuleResult::isTriggered)
                    .peek(r -> firedRules.add(r.getRuleName()))
                    .mapToInt(RuleResult::getRiskContribution)
                    .max()
                    .orElse(0);
            peak = Math.max(peak, strongest);
            if (strongest >= DETECTION_THRESHOLD) detected++;
        }

        double rate = synthetic.isEmpty() ? 0 : (double) detected / synthetic.size();
        String outcome = rate >= 0.8 ? "PASS" : rate >= 0.4 ? "PARTIAL" : "FAIL";

        List<String> gaps = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        if (!"PASS".equals(outcome)) {
            gaps.add(String.format("%.0f%% of the %s pattern went undetected",
                    (1 - rate) * 100, scenario.name()));
            if (firedRules.isEmpty()) {
                gaps.add("No rule fired at any point during the simulation");
            } else {
                gaps.add("Only these rules engaged: " + String.join(", ", firedRules));
            }
            recommendations.addAll(scenario.recommendations());
        }

        AttackSimulation simulation = simulationRepository.save(AttackSimulation.builder()
                .organizationId(orgId)
                .scenarioKey(scenario.key())
                .scenarioName(scenario.name())
                .description(scenario.description())
                .transactionsSimulated(synthetic.size())
                .detectedCount(detected)
                .detectionRate(Math.round(rate * 1000) / 1000.0)
                .peakScore(peak)
                .outcome(outcome)
                .gaps(gaps.isEmpty() ? List.of("No gaps found") : gaps)
                .recommendations(recommendations.isEmpty() ? List.of("No action needed") : recommendations)
                .build());

        agentRunRepository.save(AgentRun.builder()
                .organizationId(orgId)
                .agentName(AGENT_NAME)
                .targetType("CONTROL")
                .targetId(scenario.key())
                .verdict("PASS".equals(outcome) ? AgentVerdict.BENIGN : AgentVerdict.SUSPICIOUS)
                .confidence(90)
                .actionExecuted(false)
                .rationale(scenario.name() + ": " + detected + " of " + synthetic.size()
                        + " simulated transactions were caught (" + outcome + ")")
                .evidence(gaps)
                .durationMs(System.currentTimeMillis() - start)
                .llmAssisted(false)
                .build());

        return simulation;
    }

    public List<Scenario> library() {
        return List.of(
                new Scenario("CARD_TESTING", "Card testing",
                        "Dozens of micro-authorisations used to validate stolen card numbers before a large purchase.",
                        () -> burst(40, new BigDecimal("1.20"), "MERCHANT_TEST_1", "US", 2),
                        List.of("Add a rule for more than 10 sub-5 payments from one customer within an hour",
                                "Score micro-amounts as elevated rather than low risk")),

                new Scenario("BIN_ATTACK", "BIN attack",
                        "Sequential card numbers in one issuing range probed against a single merchant.",
                        () -> burst(30, new BigDecimal("3.50"), "MERCHANT_PROBE", "US", 1),
                        List.of("Add a merchant-level velocity rule on distinct customers per minute")),

                new Scenario("ACCOUNT_TAKEOVER", "Account takeover drain",
                        "After a credential compromise, the balance leaves in a rapid sequence of escalating payments.",
                        () -> escalating(8, new BigDecimal("400"), "MERCHANT_DRAIN", "GB"),
                        List.of("Add a rule for escalating amounts to a new payee within one session",
                                "Require step-up authentication after a password reset")),

                new Scenario("MULE_FAN_IN", "Mule fan-in",
                        "Many unrelated customers pay one freshly opened beneficiary in small amounts.",
                        () -> fanIn(25, new BigDecimal("450"), "BENEFICIARY_MULE_9"),
                        List.of("Run the beneficiary sweep more frequently",
                                "Block payments to beneficiaries scoring above 80 for mule behaviour")),

                new Scenario("STRUCTURING", "Structuring",
                        "Payments repeatedly stopped just below the reporting threshold.",
                        () -> burst(6, new BigDecimal("9400"), "MERCHANT_STRUCT", "AE", 1440),
                        List.of("Enable the structuring detector in transaction monitoring",
                                "Aggregate near-threshold payments across a rolling week")),

                new Scenario("IMPOSSIBLE_TRAVEL", "Impossible travel",
                        "The same customer transacts in two distant countries minutes apart.",
                        () -> travel(),
                        List.of("Verify the impossible-travel rule is enabled and has country data")),

                new Scenario("HIGH_VALUE_NEW_DEVICE", "High value from a new device",
                        "A large payment from a device never seen on the account before.",
                        () -> single(new BigDecimal("22000"), "MERCHANT_NEW", "SG", "device-unseen-77"),
                        List.of("Require step-up authentication for high-value payments from unrecognised devices"))
        );
    }

    /* ---------- Synthetic traffic generators ---------- */

    private List<Transaction> burst(int count, BigDecimal amount, String merchant,
                                    String country, int minutesApart) {
        List<Transaction> transactions = new ArrayList<>();
        Instant base = Instant.now().minus(2, ChronoUnit.HOURS);
        for (int i = 0; i < count; i++) {
            transactions.add(synthetic(amount, merchant, country, "redteam-device-1",
                    "redteam-user-1", base.plus((long) i * minutesApart, ChronoUnit.MINUTES)));
        }
        return transactions;
    }

    private List<Transaction> escalating(int count, BigDecimal start, String merchant, String country) {
        List<Transaction> transactions = new ArrayList<>();
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        BigDecimal amount = start;
        for (int i = 0; i < count; i++) {
            transactions.add(synthetic(amount, merchant, country, "redteam-device-2",
                    "redteam-user-2", base.plus((long) i * 5, ChronoUnit.MINUTES)));
            amount = amount.multiply(new BigDecimal("1.8"));
        }
        return transactions;
    }

    private List<Transaction> fanIn(int payers, BigDecimal amount, String beneficiary) {
        List<Transaction> transactions = new ArrayList<>();
        Instant base = Instant.now().minus(6, ChronoUnit.HOURS);
        for (int i = 0; i < payers; i++) {
            transactions.add(synthetic(amount, beneficiary, "GB", "redteam-device-" + i,
                    "redteam-payer-" + i, base.plus((long) i * 7, ChronoUnit.MINUTES)));
        }
        return transactions;
    }

    private List<Transaction> travel() {
        Instant base = Instant.now().minus(30, ChronoUnit.MINUTES);
        return List.of(
                synthetic(new BigDecimal("300"), "MERCHANT_A", "GB", "redteam-device-3",
                        "redteam-user-3", base),
                synthetic(new BigDecimal("900"), "MERCHANT_B", "BR", "redteam-device-3",
                        "redteam-user-3", base.plus(12, ChronoUnit.MINUTES)));
    }

    private List<Transaction> single(BigDecimal amount, String merchant, String country, String device) {
        return List.of(synthetic(amount, merchant, country, device, "redteam-user-4", Instant.now()));
    }

    /**
     * Built but never saved. The id is assigned locally so rules that log or
     * key on it behave normally.
     */
    private Transaction synthetic(BigDecimal amount, String merchant, String country,
                                  String device, String user, Instant timestamp) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionExternalId("redteam-" + UUID.randomUUID())
                .userId(user)
                .merchantId(merchant)
                .amount(amount)
                .currency("USD")
                .country(country)
                .deviceId(device)
                .ipAddress("203.0.113." + Math.abs(user.hashCode() % 250))
                .status(TransactionStatus.PENDING)
                .timestamp(timestamp)
                .build();
    }

    /** One attack pattern: how to generate it and what to do if it is missed. */
    public record Scenario(String key, String name, String description,
                           Supplier<List<Transaction>> generator, List<String> recommendations) {
    }
}
