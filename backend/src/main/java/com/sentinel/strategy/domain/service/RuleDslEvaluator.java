package com.sentinel.strategy.domain.service;

import com.sentinel.strategy.domain.model.RuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluates a stored rule against a feature vector.
 *
 * <p>A deliberately small condition language — field, operator, value, joined
 * by ALL or ANY. It is not a general expression evaluator and it never will
 * be: rules authored by analysts are read by auditors and replayed years
 * later, and anything that can express arbitrary logic can also express logic
 * nobody can review. Everything complex belongs in a feature, where it is
 * named, documented, and testable.
 */
@Component
@Slf4j
public class RuleDslEvaluator {

    public Result evaluate(RuleDefinition rule, Map<String, Object> features) {
        Map<String, Object> definition = rule.getDefinition();
        if (definition == null || definition.isEmpty()) {
            return new Result(false, List.of("Rule has no conditions"));
        }

        String match = String.valueOf(definition.getOrDefault("match", "ALL")).toUpperCase(Locale.ROOT);

        Object raw = definition.get("conditions");
        if (!(raw instanceof List<?> conditions) || conditions.isEmpty()) {
            return new Result(false, List.of("Rule has no conditions"));
        }

        List<String> satisfied = new ArrayList<>();
        int met = 0;
        for (Object item : conditions) {
            if (!(item instanceof Map<?, ?> condition)) continue;
            String field = String.valueOf(condition.get("field"));
            String operator = String.valueOf(condition.get("operator")).toUpperCase(Locale.ROOT);
            Object expected = condition.get("value");
            Object actual = features.get(field);

            if (test(actual, operator, expected)) {
                met++;
                satisfied.add(field + " " + humanise(operator) + " " + expected + " (actual " + actual + ")");
            }
        }

        boolean triggered = "ANY".equals(match) ? met > 0 : met == conditions.size();
        return new Result(triggered, satisfied);
    }

    private boolean test(Object actual, String operator, Object expected) {
        if (actual == null) {
            // A missing feature is not a match. Rules must not fire on absence,
            // or a feed outage turns into a mass block.
            return "IS_NULL".equals(operator);
        }

        return switch (operator) {
            case "EQ" -> stringOf(actual).equalsIgnoreCase(stringOf(expected));
            case "NEQ" -> !stringOf(actual).equalsIgnoreCase(stringOf(expected));
            case "GT" -> compare(actual, expected) > 0;
            case "GTE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LTE" -> compare(actual, expected) <= 0;
            case "IN" -> inList(actual, expected);
            case "NOT_IN" -> !inList(actual, expected);
            case "CONTAINS" -> stringOf(actual).toLowerCase(Locale.ROOT)
                    .contains(stringOf(expected).toLowerCase(Locale.ROOT));
            case "IS_TRUE" -> Boolean.TRUE.equals(actual) || "true".equalsIgnoreCase(stringOf(actual));
            case "IS_FALSE" -> Boolean.FALSE.equals(actual) || "false".equalsIgnoreCase(stringOf(actual));
            case "IS_NULL" -> false;
            default -> {
                log.warn("Unknown rule operator: {}", operator);
                yield false;
            }
        };
    }

    private int compare(Object actual, Object expected) {
        try {
            return new BigDecimal(stringOf(actual)).compareTo(new BigDecimal(stringOf(expected)));
        } catch (NumberFormatException e) {
            return stringOf(actual).compareTo(stringOf(expected));
        }
    }

    private boolean inList(Object actual, Object expected) {
        if (expected instanceof List<?> list) {
            return list.stream().anyMatch(v -> stringOf(v).equalsIgnoreCase(stringOf(actual)));
        }
        return stringOf(expected).toLowerCase(Locale.ROOT)
                .contains(stringOf(actual).toLowerCase(Locale.ROOT));
    }

    private String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String humanise(String operator) {
        return switch (operator) {
            case "EQ" -> "equals";
            case "NEQ" -> "does not equal";
            case "GT" -> "is above";
            case "GTE" -> "is at or above";
            case "LT" -> "is below";
            case "LTE" -> "is at or below";
            case "IN" -> "is one of";
            case "NOT_IN" -> "is not one of";
            case "CONTAINS" -> "contains";
            case "IS_TRUE" -> "is true";
            case "IS_FALSE" -> "is false";
            default -> operator.toLowerCase(Locale.ROOT);
        };
    }

    /** Whether the rule fired, and which conditions carried it. */
    public record Result(boolean triggered, List<String> satisfiedConditions) {
    }
}
