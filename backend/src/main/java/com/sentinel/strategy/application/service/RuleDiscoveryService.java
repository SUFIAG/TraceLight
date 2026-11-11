package com.sentinel.strategy.application.service;

import com.sentinel.featurestore.application.service.FeatureComputationService;
import com.sentinel.featurestore.domain.model.FeatureDefinition;
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
import java.util.*;

/**
 * Mines confirmed fraud for rules nobody has written yet.
 *
 * <p>For each numeric feature, it compares the distribution over fraudulent
 * transactions with the distribution over legitimate ones, and proposes a
 * threshold where the separation is widest. Candidates are then measured on
 * the same sample so each arrives with its own precision and expected queue
 * cost rather than as an untested suggestion.
 *
 * <p>Nothing here deploys anything. Discovery produces candidates; a human
 * reads them, backtests them, and decides. An engine that silently writes its
 * own controls is one bad label away from blocking a whole customer segment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleDiscoveryService {

    /** Candidates below this precision are noise and are not reported. */
    private static final double MIN_PRECISION = 0.25;
    /** A candidate must be at least this much more common in fraud than in legitimate traffic. */
    private static final double MIN_LIFT = 2.0;

    private static final List<FeatureDefinition> NUMERIC_CANDIDATES = List.of(
            FeatureDefinition.AMOUNT,
            FeatureDefinition.TX_COUNT_1H,
            FeatureDefinition.TX_COUNT_24H,
            FeatureDefinition.AMOUNT_SUM_24H,
            FeatureDefinition.DISTINCT_MERCHANTS_24H,
            FeatureDefinition.DISTINCT_COUNTRIES_24H,
            FeatureDefinition.AMOUNT_RATIO_TO_AVERAGE,
            FeatureDefinition.BENEFICIARY_RISK,
            FeatureDefinition.GRAPH_DEGREE);

    private final TransactionRepository transactionRepository;
    private final FeatureComputationService featureComputationService;

    @Transactional(readOnly = true)
    public List<Candidate> discover(UUID orgId, int days, int sampleLimit) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Transaction> sample = transactionRepository
                .findByOrganizationId(orgId, PageRequest.of(0, sampleLimit, Sort.by("timestamp").descending()))
                .getContent().stream()
                .filter(t -> t.getTimestamp().isAfter(since))
                .toList();

        List<Map<String, Object>> fraudVectors = new ArrayList<>();
        List<Map<String, Object>> cleanVectors = new ArrayList<>();

        for (Transaction transaction : sample) {
            Map<String, Object> features = featureComputationService.compute(orgId, transaction);
            if (isFraud(transaction)) {
                fraudVectors.add(features);
            } else {
                cleanVectors.add(features);
            }
        }

        if (fraudVectors.size() < 5) {
            log.info("Rule discovery for org {} skipped: only {} fraud examples", orgId, fraudVectors.size());
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (FeatureDefinition feature : NUMERIC_CANDIDATES) {
            threshold(feature, fraudVectors, cleanVectors).ifPresent(candidates::add);
        }
        categorical(FeatureDefinition.COUNTRY, fraudVectors, cleanVectors).ifPresent(candidates::add);
        booleanFlag(FeatureDefinition.IS_NEW_PAYEE, fraudVectors, cleanVectors).ifPresent(candidates::add);

        candidates.sort((a, b) -> Double.compare(b.lift(), a.lift()));
        log.info("Rule discovery for org {}: {} candidates from {} fraud and {} clean examples",
                orgId, candidates.size(), fraudVectors.size(), cleanVectors.size());
        return candidates;
    }

    /**
     * Picks the fraud distribution's lower quartile as the threshold. The
     * median would catch only half the fraud; the minimum would catch
     * everything including the legitimate traffic around it.
     */
    private Optional<Candidate> threshold(FeatureDefinition feature,
                                          List<Map<String, Object>> fraud,
                                          List<Map<String, Object>> clean) {
        List<Double> fraudValues = numeric(feature, fraud);
        if (fraudValues.size() < 5) return Optional.empty();

        Collections.sort(fraudValues);
        double candidateThreshold = fraudValues.get(fraudValues.size() / 4);
        if (candidateThreshold <= 0) return Optional.empty();

        long fraudHits = fraudValues.stream().filter(v -> v >= candidateThreshold).count();
        long cleanHits = numeric(feature, clean).stream().filter(v -> v >= candidateThreshold).count();

        return assess(feature, "GTE", trim(candidateThreshold), fraudHits, cleanHits, fraud.size(), clean.size());
    }

    private Optional<Candidate> categorical(FeatureDefinition feature,
                                            List<Map<String, Object>> fraud,
                                            List<Map<String, Object>> clean) {
        Map<String, Long> fraudCounts = new HashMap<>();
        for (Map<String, Object> vector : fraud) {
            Object value = vector.get(feature.getKey());
            if (value != null) fraudCounts.merge(String.valueOf(value), 1L, Long::sum);
        }
        if (fraudCounts.isEmpty()) return Optional.empty();

        String dominant = fraudCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (dominant == null) return Optional.empty();

        long fraudHits = fraudCounts.get(dominant);
        long cleanHits = clean.stream()
                .filter(v -> dominant.equals(String.valueOf(v.get(feature.getKey()))))
                .count();

        return assess(feature, "EQ", dominant, fraudHits, cleanHits, fraud.size(), clean.size());
    }

    private Optional<Candidate> booleanFlag(FeatureDefinition feature,
                                            List<Map<String, Object>> fraud,
                                            List<Map<String, Object>> clean) {
        long fraudHits = fraud.stream().filter(v -> Boolean.TRUE.equals(v.get(feature.getKey()))).count();
        long cleanHits = clean.stream().filter(v -> Boolean.TRUE.equals(v.get(feature.getKey()))).count();
        return assess(feature, "IS_TRUE", true, fraudHits, cleanHits, fraud.size(), clean.size());
    }

    private Optional<Candidate> assess(FeatureDefinition feature, String operator, Object value,
                                       long fraudHits, long cleanHits, int fraudTotal, int cleanTotal) {
        if (fraudHits == 0) return Optional.empty();

        double fraudRate = (double) fraudHits / Math.max(1, fraudTotal);
        double cleanRate = (double) cleanHits / Math.max(1, cleanTotal);
        double lift = cleanRate == 0 ? Double.MAX_VALUE / 2 : fraudRate / cleanRate;
        double precision = (double) fraudHits / Math.max(1, fraudHits + cleanHits);

        if (precision < MIN_PRECISION || lift < MIN_LIFT) return Optional.empty();

        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", feature.getKey());
        condition.put("operator", operator);
        condition.put("value", value);

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("match", "ALL");
        definition.put("conditions", List.of(condition));

        String sentence = feature.getLabel() + " " + readable(operator) + " " + value;
        String rationale = String.format(
                "Present in %.0f%% of confirmed fraud but only %.0f%% of legitimate traffic (%.1fx lift). "
                        + "On this sample the condition would hit %d fraudulent and %d legitimate transactions.",
                fraudRate * 100, cleanRate * 100, Math.min(lift, 999), fraudHits, cleanHits);

        int riskPoints = (int) Math.min(95, Math.round(precision * 100));

        return Optional.of(new Candidate(
                "Discovered: " + feature.getLabel(), sentence, definition, riskPoints,
                round(precision), round(lift), fraudHits, cleanHits, rationale));
    }

    private List<Double> numeric(FeatureDefinition feature, List<Map<String, Object>> vectors) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> vector : vectors) {
            Object raw = vector.get(feature.getKey());
            if (raw instanceof Number number) {
                values.add(number.doubleValue());
            } else if (raw instanceof BigDecimal decimal) {
                values.add(decimal.doubleValue());
            }
        }
        return values;
    }

    private boolean isFraud(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.FLAGGED
                || transaction.getStatus() == TransactionStatus.REJECTED;
    }

    private Object trim(double value) {
        return value == Math.floor(value) ? (long) value : Math.round(value * 100) / 100.0;
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private String readable(String operator) {
        return switch (operator) {
            case "GTE" -> "is at or above";
            case "EQ" -> "equals";
            case "IS_TRUE" -> "is true";
            default -> operator;
        };
    }

    /** A proposed rule, already measured. */
    public record Candidate(String name, String sentence, Map<String, Object> definition, int riskPoints,
                            double precision, double lift, long fraudHits, long cleanHits, String rationale) {
    }
}
