package com.sentinel.identity.application.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Name similarity for list screening.
 *
 * <p>Token-set similarity rather than raw edit distance on the whole string.
 * Real name data is reordered ("Mohammed Al Fulan" / "Al Fulan Mohammed"),
 * partially transliterated, and inconsistently punctuated, and plain
 * Levenshtein over the full string punishes word order far more than it
 * punishes a genuinely different name.
 */
@Component
public class NameMatcher {

    /** Honorifics and corporate suffixes carry no identifying information. */
    private static final List<String> NOISE = List.of(
            "mr", "mrs", "ms", "dr", "prof", "sir", "hon",
            "ltd", "limited", "llc", "inc", "plc", "gmbh", "sa", "bv");

    public String normalise(String name) {
        if (name == null) return "";
        String stripped = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return Arrays.stream(stripped.split(" "))
                .filter(token -> !token.isBlank() && !NOISE.contains(token))
                .sorted()
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    /** 0-100. 100 means the normalised token sets are identical. */
    public int similarity(String a, String b) {
        String left = normalise(a);
        String right = normalise(b);
        if (left.isEmpty() || right.isEmpty()) return 0;
        if (left.equals(right)) return 100;

        String[] leftTokens = left.split(" ");
        String[] rightTokens = right.split(" ");

        // Every token in the shorter name is matched to its best counterpart in
        // the longer one, so an extra middle name costs little but a different
        // surname costs a great deal.
        String[] shorter = leftTokens.length <= rightTokens.length ? leftTokens : rightTokens;
        String[] longer = leftTokens.length <= rightTokens.length ? rightTokens : leftTokens;

        double total = 0;
        for (String token : shorter) {
            double best = 0;
            for (String candidate : longer) {
                best = Math.max(best, tokenSimilarity(token, candidate));
            }
            total += best;
        }

        double coverage = total / shorter.length;
        // Missing tokens still matter: penalise by the length difference.
        double lengthPenalty = 1.0 - (0.15 * (longer.length - shorter.length) / (double) longer.length);
        return (int) Math.round(Math.max(0, coverage * lengthPenalty) * 100);
    }

    private double tokenSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int distance = levenshtein(a, b);
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 0 : 1.0 - ((double) distance / longest);
    }

    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
