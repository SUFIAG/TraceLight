package com.sentinel.governance.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Guardrails for anything that leaves for, or arrives from, a language model.
 *
 * <p>Two obligations, both of which a fraud platform fails by default:
 *
 * <ul>
 *   <li><b>Nothing identifying goes out.</b> Prompts assembled from case data
 *       contain names, card numbers, emails, and account identifiers. Sending
 *       those to an external model is a disclosure, whatever the vendor's
 *       retention policy says.</li>
 *   <li><b>Nothing unsupported comes back.</b> A model asked to explain a
 *       decision will happily produce a confident figure that appears nowhere
 *       in the evidence. Every number in an AI narrative is checked against
 *       the evidence it was given, and unsupported ones are flagged rather
 *       than silently shown to an analyst.</li>
 * </ul>
 */
@Service
@Slf4j
public class LlmGuardrailService {

    private static final Pattern PAN = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{8,16}\\d");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\b");

    /** Phrases that indicate the model is asserting rather than reasoning from evidence. */
    private static final List<String> OVERCONFIDENT = List.of(
            "definitely fraud", "certainly fraudulent", "without any doubt",
            "guaranteed", "100% certain", "proven fraud");

    /**
     * Redacts identifying data before a prompt leaves the platform. Order
     * matters: the longest and most specific patterns run first, so a card
     * number is not partly consumed by the phone-number pattern.
     */
    public Redaction redact(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new Redaction("", List.of());
        }

        List<String> redacted = new ArrayList<>();
        String output = prompt;

        output = replace(output, PAN, "[CARD_REDACTED]", redacted, "card number");
        output = replace(output, IBAN, "[IBAN_REDACTED]", redacted, "IBAN");
        output = replace(output, SSN, "[NATIONAL_ID_REDACTED]", redacted, "national identifier");
        output = replace(output, EMAIL, "[EMAIL_REDACTED]", redacted, "email address");
        output = replace(output, PHONE, "[PHONE_REDACTED]", redacted, "phone number");

        return new Redaction(output, redacted);
    }

    /**
     * Checks a model's narrative against the evidence it was given.
     *
     * @param narrative the model's output
     * @param evidence  the facts the model was supplied with
     */
    public Verification verify(String narrative, List<String> evidence) {
        List<String> issues = new ArrayList<>();

        if (narrative == null || narrative.isBlank()) {
            return new Verification(false, List.of("The model returned nothing"), 0);
        }

        String evidenceCorpus = String.join(" ", evidence == null ? List.of() : evidence);

        // Every figure in the narrative should be traceable to the evidence.
        List<String> unsupported = new ArrayList<>();
        NUMBER.matcher(narrative).results()
                .map(match -> match.group())
                .filter(number -> number.length() > 2)
                .distinct()
                .filter(number -> !evidenceCorpus.contains(number))
                .limit(10)
                .forEach(unsupported::add);

        if (!unsupported.isEmpty()) {
            issues.add("Figures not present in the evidence: " + String.join(", ", unsupported));
        }

        String lower = narrative.toLowerCase();
        OVERCONFIDENT.stream()
                .filter(lower::contains)
                .forEach(phrase -> issues.add("Overconfident language: \"" + phrase + "\""));

        if (narrative.length() < 40) {
            issues.add("The narrative is too short to constitute an explanation");
        }

        int confidence = Math.max(0, 100 - issues.size() * 25 - unsupported.size() * 5);
        boolean passed = issues.isEmpty();

        if (!passed) {
            log.warn("Guardrail check flagged {} issue(s) in a model narrative", issues.size());
        }
        return new Verification(passed, issues, confidence);
    }

    private String replace(String input, Pattern pattern, String replacement,
                           List<String> redacted, String label) {
        if (pattern.matcher(input).find()) {
            redacted.add(label);
            return pattern.matcher(input).replaceAll(replacement);
        }
        return input;
    }

    public record Redaction(String prompt, List<String> redactedTypes) {
    }

    public record Verification(boolean passed, List<String> issues, int confidence) {
    }
}
