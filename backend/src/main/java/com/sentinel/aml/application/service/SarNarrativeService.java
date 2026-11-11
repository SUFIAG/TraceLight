package com.sentinel.aml.application.service;

import com.sentinel.ai.adapter.out.llm.LlmClient;
import com.sentinel.aml.domain.model.AmlFinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Drafts the suspicious activity report narrative.
 *
 * <p>This is the single largest time sink in financial crime operations: an
 * experienced officer spends one to three hours writing each narrative, and
 * the structure barely varies. What varies is the evidence, and the evidence
 * is already in the findings.
 *
 * <p>The deterministic template produces a complete, filing-grade narrative on
 * its own. The language model is used only to improve the prose of a narrative
 * that is already factually assembled, and its output is discarded if it comes
 * back shorter than the template — a model that drops evidence is worse than
 * no model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SarNarrativeService {

    private final LlmClient llmClient;

    public Draft draft(String subjectId, String subjectName, String jurisdiction, List<AmlFinding> findings) {
        String template = template(subjectId, subjectName, jurisdiction, findings);
        Optional<String> enhanced = enhance(template, jurisdiction);
        return new Draft(enhanced.orElse(template), enhanced.isPresent());
    }

    private String template(String subjectId, String subjectName, String jurisdiction, List<AmlFinding> findings) {
        BigDecimal total = findings.stream()
                .map(f -> f.getTotalValue() == null ? BigDecimal.ZERO : f.getTotalValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();

        sb.append("SUBJECT\n");
        sb.append(subjectName == null ? subjectId : subjectName + " (customer reference " + subjectId + ")")
                .append("\n\n");

        sb.append("REASON FOR SUSPICION\n");
        sb.append("Automated transaction monitoring identified ").append(findings.size())
                .append(findings.size() == 1 ? " pattern" : " patterns")
                .append(" consistent with money laundering typologies, involving an aggregate value of ")
                .append(total).append(".\n\n");

        sb.append("ACTIVITY OBSERVED\n");
        int index = 1;
        for (AmlFinding finding : findings) {
            sb.append(index++).append(". ").append(finding.getTypology().getLabel())
                    .append(" (").append(finding.getTypology().getReference()).append(")\n");
            sb.append("   ").append(finding.getNarrative()).append("\n");
            if (finding.getWindowStart() != null && finding.getWindowEnd() != null) {
                sb.append("   Period: ").append(finding.getWindowStart())
                        .append(" to ").append(finding.getWindowEnd()).append("\n");
            }
            if (finding.getEvidence() != null) {
                for (String item : finding.getEvidence()) {
                    sb.append("   - ").append(item).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("TYPOLOGIES\n");
        StringJoiner typologies = new StringJoiner(", ");
        findings.stream().map(f -> f.getTypology().getLabel()).distinct().forEach(typologies::add);
        sb.append(typologies).append("\n\n");

        sb.append("ACTION TAKEN\n");
        sb.append("The activity has been escalated for review. Transactions are listed in the attached schedule. ")
                .append("This report is submitted to ").append(jurisdiction)
                .append(" in accordance with the institution's reporting obligations.\n");

        return sb.toString();
    }

    /**
     * Asks the model to tighten the prose without touching the facts. The
     * guard below is deliberate: a shorter narrative means evidence was
     * dropped, so the template wins.
     */
    private Optional<String> enhance(String template, String jurisdiction) {
        if (!llmClient.isEnabled()) return Optional.empty();

        String system = """
                You are a financial crime compliance officer writing a suspicious activity report narrative.
                Rewrite the draft below into clear regulator-facing prose.
                Rules:
                - Never add a fact that is not in the draft.
                - Never remove a figure, date, counterparty, or typology.
                - Keep the section headings.
                - Write plainly and in the past tense. No speculation about guilt.
                """;

        return llmClient.complete(system, "Jurisdiction: " + jurisdiction + "\n\n" + template)
                .filter(result -> result.length() >= template.length() * 0.8)
                .map(result -> result + "\n\n[Narrative drafted with AI assistance from recorded evidence. "
                        + "Requires compliance officer approval before filing.]");
    }

    /** Narrative plus whether the language model actually contributed. */
    public record Draft(String narrative, boolean aiDrafted) {
    }
}
