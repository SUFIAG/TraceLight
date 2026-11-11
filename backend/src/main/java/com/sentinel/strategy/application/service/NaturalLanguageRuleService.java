package com.sentinel.strategy.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.ai.adapter.out.llm.LlmClient;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.featurestore.domain.model.FeatureDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a sentence into a rule.
 *
 * <p>"Block transfers over 5,000 to new payees within 24 hours of a password
 * reset" should become a rule an analyst can review, backtest, and ship,
 * without a developer. Two paths produce it:
 *
 * <ol>
 *   <li>A deterministic parser for the shapes that cover most real rules —
 *       amount comparisons, velocity counts, country lists, boolean flags.</li>
 *   <li>The language model, which handles the rest and is asked for strict
 *       JSON against the published feature catalogue.</li>
 * </ol>
 *
 * <p>Whatever produces the condition tree, it is validated against the feature
 * catalogue before it is returned. A rule referring to a feature the platform
 * does not compute would silently never fire, which is the worst possible
 * failure for a control somebody believes is live.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageRuleService {

    private static final Pattern AMOUNT_ABOVE = Pattern.compile(
            "(?:over|above|greater than|more than|exceed(?:s|ing)?)\\s*[\\p{Sc}]?\\s*([0-9][0-9,.]*)\\s*(k|m)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_BELOW = Pattern.compile(
            "(?:under|below|less than|smaller than)\\s*[\\p{Sc}]?\\s*([0-9][0-9,.]*)\\s*(k|m)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COUNT_IN_WINDOW = Pattern.compile(
            "(?:more than|over|at least)\\s*([0-9]+)\\s*(?:payments?|transactions?|transfers?)\\s*" +
                    "(?:in|within|per)\\s*(?:the\\s*)?(?:last\\s*)?([0-9]*)\\s*(hour|hours|day|days|week|weeks)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COUNTRY_LIST = Pattern.compile(
            "(?:from|to|in)\\s+((?:[A-Z]{2})(?:\\s*,\\s*[A-Z]{2})*)\\b");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /** Parses the sentence into a condition tree and explains how it was read. */
    public Parsed parse(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            throw new BusinessException("EMPTY_RULE", "Describe the rule in a sentence");
        }

        List<Map<String, Object>> conditions = new ArrayList<>(deterministic(sentence));
        boolean usedLlm = false;

        if (conditions.isEmpty()) {
            Optional<List<Map<String, Object>>> inferred = byLanguageModel(sentence);
            if (inferred.isPresent()) {
                conditions.addAll(inferred.get());
                usedLlm = true;
            }
        }

        if (conditions.isEmpty()) {
            throw new BusinessException("UNPARSEABLE_RULE",
                    "Could not turn that into a rule. Try naming a feature and a threshold, " +
                            "for example: amount above 5000 and more than 3 payments in 24 hours.");
        }

        List<String> unknown = conditions.stream()
                .map(c -> String.valueOf(c.get("field")))
                .filter(field -> FeatureDefinition.byKey(field).isEmpty())
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("UNKNOWN_FEATURE",
                    "These features do not exist in the catalogue: " + String.join(", ", unknown));
        }

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("match", sentence.toLowerCase(Locale.ROOT).contains(" or ") ? "ANY" : "ALL");
        definition.put("conditions", conditions);

        return new Parsed(definition, explain(definition), usedLlm);
    }

    /* ---------- Deterministic parsing ---------- */

    private List<Map<String, Object>> deterministic(String sentence) {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Matcher above = AMOUNT_ABOVE.matcher(sentence);
        if (above.find()) {
            conditions.add(condition(FeatureDefinition.AMOUNT.getKey(), "GT", number(above.group(1), above.group(2))));
        }

        Matcher below = AMOUNT_BELOW.matcher(sentence);
        if (below.find()) {
            conditions.add(condition(FeatureDefinition.AMOUNT.getKey(), "LT", number(below.group(1), below.group(2))));
        }

        Matcher count = COUNT_IN_WINDOW.matcher(sentence);
        if (count.find()) {
            int threshold = Integer.parseInt(count.group(1));
            String quantity = count.group(2);
            String unit = count.group(3).toLowerCase(Locale.ROOT);
            int size = quantity == null || quantity.isBlank() ? 1 : Integer.parseInt(quantity);
            conditions.add(condition(velocityFeature(unit, size), "GT", threshold));
        }

        Matcher countries = COUNTRY_LIST.matcher(sentence);
        if (countries.find()) {
            List<String> codes = Arrays.stream(countries.group(1).split(","))
                    .map(String::trim)
                    .filter(code -> code.length() == 2)
                    .toList();
            if (!codes.isEmpty()) {
                conditions.add(condition(FeatureDefinition.COUNTRY.getKey(), "IN", codes));
            }
        }

        String lower = sentence.toLowerCase(Locale.ROOT);
        if (lower.contains("new payee") || lower.contains("first payment") || lower.contains("new beneficiary")) {
            conditions.add(condition(FeatureDefinition.IS_NEW_PAYEE.getKey(), "IS_TRUE", true));
        }
        if (lower.contains("mule")) {
            conditions.add(condition(FeatureDefinition.BENEFICIARY_RISK.getKey(), "GTE", 50));
        }
        if (lower.contains("night") || lower.contains("off hours") || lower.contains("off-hours")) {
            conditions.add(condition(FeatureDefinition.HOUR_OF_DAY.getKey(), "LT", 6));
        }

        return conditions;
    }

    private String velocityFeature(String unit, int size) {
        if (unit.startsWith("hour")) {
            return size <= 1 ? FeatureDefinition.TX_COUNT_1H.getKey() : FeatureDefinition.TX_COUNT_24H.getKey();
        }
        if (unit.startsWith("day")) {
            return size <= 1 ? FeatureDefinition.TX_COUNT_24H.getKey() : FeatureDefinition.TX_COUNT_7D.getKey();
        }
        return FeatureDefinition.TX_COUNT_7D.getKey();
    }

    private BigDecimal number(String digits, String multiplier) {
        BigDecimal value = new BigDecimal(digits.replace(",", ""));
        if (multiplier == null) return value;
        return switch (multiplier.toLowerCase(Locale.ROOT)) {
            case "k" -> value.multiply(BigDecimal.valueOf(1_000));
            case "m" -> value.multiply(BigDecimal.valueOf(1_000_000));
            default -> value;
        };
    }

    /* ---------- Language model fallback ---------- */

    @SuppressWarnings("unchecked")
    private Optional<List<Map<String, Object>>> byLanguageModel(String sentence) {
        if (!llmClient.isEnabled()) return Optional.empty();

        StringBuilder catalogue = new StringBuilder();
        for (FeatureDefinition feature : FeatureDefinition.values()) {
            catalogue.append("- ").append(feature.getKey()).append(" (").append(feature.getType())
                    .append(", window ").append(feature.getWindow()).append("): ")
                    .append(feature.getDescription()).append('\n');
        }

        String system = """
                You convert a fraud analyst's sentence into a rule condition list.
                Reply with raw JSON only: {"conditions":[{"field":"...","operator":"...","value":...}]}
                Operators: EQ, NEQ, GT, GTE, LT, LTE, IN, NOT_IN, CONTAINS, IS_TRUE, IS_FALSE.
                Use only fields from the catalogue below. Never invent a field.
                If the sentence cannot be expressed with these fields, reply {"conditions":[]}.

                Catalogue:
                """ + catalogue;

        return llmClient.complete(system, sentence).flatMap(response -> {
            try {
                String json = response.trim()
                        .replaceAll("^```(?:json)?", "")
                        .replaceAll("```$", "")
                        .trim();
                Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
                Object conditions = parsed.get("conditions");
                if (conditions instanceof List<?> list && !list.isEmpty()) {
                    return Optional.of((List<Map<String, Object>>) list);
                }
                return Optional.empty();
            } catch (Exception e) {
                log.warn("Language model returned an unusable rule definition: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    /* ---------- Explanation ---------- */

    /** Plain-English readback, so the author can confirm it says what they meant. */
    @SuppressWarnings("unchecked")
    public String explain(Map<String, Object> definition) {
        Object raw = definition.get("conditions");
        if (!(raw instanceof List<?> conditions) || conditions.isEmpty()) {
            return "This rule has no conditions and will never fire.";
        }
        String joiner = "ANY".equalsIgnoreCase(String.valueOf(definition.get("match"))) ? " OR " : " AND ";

        StringJoiner parts = new StringJoiner(joiner);
        for (Object item : conditions) {
            Map<String, Object> condition = (Map<String, Object>) item;
            String field = String.valueOf(condition.get("field"));
            String operator = String.valueOf(condition.get("operator")).toUpperCase(Locale.ROOT);
            String label = FeatureDefinition.byKey(field).map(FeatureDefinition::getLabel).orElse(field);
            // IS_TRUE and IS_FALSE carry their value in the operator itself, so
            // appending it again reads as "... is true true".
            boolean valueIsImplicit = operator.equals("IS_TRUE") || operator.equals("IS_FALSE");
            parts.add(valueIsImplicit
                    ? label + " " + readable(operator)
                    : label + " " + readable(operator) + " " + condition.get("value"));
        }
        return "Fires when " + parts + ".";
    }

    private String readable(String operator) {
        return switch (operator.toUpperCase(Locale.ROOT)) {
            case "GT" -> "is above";
            case "GTE" -> "is at or above";
            case "LT" -> "is below";
            case "LTE" -> "is at or below";
            case "EQ" -> "equals";
            case "NEQ" -> "does not equal";
            case "IN" -> "is one of";
            case "NOT_IN" -> "is not one of";
            case "CONTAINS" -> "contains";
            case "IS_TRUE" -> "is true";
            case "IS_FALSE" -> "is false";
            default -> operator;
        };
    }

    private Map<String, Object> condition(String field, String operator, Object value) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", field);
        condition.put("operator", operator);
        condition.put("value", value);
        return condition;
    }

    /** The condition tree, its readback, and whether a model was needed. */
    public record Parsed(Map<String, Object> definition, String explanation, boolean llmAssisted) {
    }
}
