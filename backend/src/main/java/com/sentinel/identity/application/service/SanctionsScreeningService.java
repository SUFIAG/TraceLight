package com.sentinel.identity.application.service;

import com.sentinel.ai.adapter.out.llm.LlmClient;
import com.sentinel.identity.adapter.out.persistence.SanctionsHitRepository;
import com.sentinel.identity.adapter.out.persistence.SanctionsListEntryRepository;
import com.sentinel.identity.domain.model.SanctionsHit;
import com.sentinel.identity.domain.model.SanctionsListEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Name screening with automated false-positive reduction.
 *
 * <p>Screening itself is easy. The expense is the aftermath: common names
 * generate matches by the thousand, and an analyst spends the day confirming
 * that this particular John Smith is not the sanctioned one. Auto-dismissal
 * here is deliberately conservative and discriminator-driven — a hit is only
 * cleared when a hard attribute (date of birth, country) positively
 * contradicts the list entry, never because the model found it unlikely.
 *
 * <p>Every dismissal keeps its rationale. "Cleared by AI" with no argument is
 * not a control a regulator will accept.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SanctionsScreeningService {

    /** Below this similarity the names are simply different people. */
    private static final int MATCH_FLOOR = 72;
    /** At or above this, no automated dismissal is permitted whatever the discriminators say. */
    private static final int AUTO_DISMISS_CEILING = 95;

    private final SanctionsListEntryRepository listRepository;
    private final SanctionsHitRepository hitRepository;
    private final NameMatcher nameMatcher;
    private final LlmClient llmClient;

    @Transactional
    public List<SanctionsHit> screen(UUID orgId, String subjectId, String fullName,
                                     String dateOfBirth, String country, UUID verificationId) {
        if (fullName == null || fullName.isBlank()) return List.of();

        List<SanctionsHit> hits = new ArrayList<>();
        for (SanctionsListEntry entry : listRepository.findByActiveTrue()) {
            int similarity = nameMatcher.similarity(fullName, entry.getFullName());
            if (similarity < MATCH_FLOOR) continue;

            SanctionsHit hit = SanctionsHit.builder()
                    .organizationId(orgId)
                    .subjectId(subjectId)
                    .verificationId(verificationId)
                    .listType(entry.getListType())
                    .listName(entry.getListName())
                    .matchedName(entry.getFullName())
                    .matchScore(similarity)
                    .matchedDob(entry.getDateOfBirth())
                    .matchedCountry(entry.getCountry())
                    .status("OPEN")
                    .dismissedByAi(false)
                    .build();

            applyFalsePositiveReduction(hit, similarity, fullName, dateOfBirth, country, entry);
            hits.add(hitRepository.save(hit));
        }

        long open = hits.stream().filter(h -> "OPEN".equals(h.getStatus())).count();
        log.info("Screened {} against {} list entries: {} hits, {} needing review",
                subjectId, listRepository.countByActiveTrue(), hits.size(), open);
        return hits;
    }

    private void applyFalsePositiveReduction(SanctionsHit hit, int similarity, String fullName,
                                             String dateOfBirth, String country, SanctionsListEntry entry) {
        if (similarity >= AUTO_DISMISS_CEILING) {
            return;
        }

        List<String> discriminators = new ArrayList<>();

        if (dateOfBirth != null && entry.getDateOfBirth() != null
                && !dateOfBirth.isBlank() && !entry.getDateOfBirth().isBlank()) {
            String subjectYear = yearOf(dateOfBirth);
            String entryYear = yearOf(entry.getDateOfBirth());
            if (subjectYear != null && entryYear != null && !subjectYear.equals(entryYear)) {
                discriminators.add("date of birth differs (" + subjectYear + " vs " + entryYear + ")");
            }
        }

        if (country != null && entry.getCountry() != null
                && !country.isBlank() && !country.equalsIgnoreCase(entry.getCountry())) {
            discriminators.add("country differs (" + country + " vs " + entry.getCountry() + ")");
        }

        if (discriminators.isEmpty()) {
            return;
        }

        String rationale = "Name similarity " + similarity + "% but " + String.join(" and ", discriminators)
                + ". Treated as a different person.";

        // The language model is asked only to sanity-check an argument the
        // deterministic logic already made. It can veto a dismissal; it can
        // never create one.
        if (llmClient.isEnabled()) {
            String system = """
                    You review sanctions screening dismissals at a bank.
                    You are given a proposed dismissal rationale.
                    Reply AGREE if the discriminating attributes genuinely rule out a match.
                    Reply DISAGREE if the match could still be the same person.
                    Reply with one word only.
                    """;
            String user = "Screened name: " + fullName + "\nList entry: " + entry.getFullName()
                    + " (" + entry.getListName() + ")\nProposed rationale: " + rationale;
            boolean vetoed = llmClient.complete(system, user)
                    .map(answer -> answer.trim().toUpperCase().startsWith("DISAGREE"))
                    .orElse(false);
            if (vetoed) {
                hit.setDismissalRationale("Automated dismissal was declined on review: " + rationale);
                return;
            }
        }

        hit.setStatus("DISMISSED");
        hit.setDismissedByAi(true);
        hit.setDismissalRationale(rationale);
    }

    @Transactional
    public SanctionsHit decide(UUID orgId, UUID hitId, String status, UUID reviewerId, String note) {
        SanctionsHit hit = hitRepository.findByIdAndOrganizationId(hitId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown screening hit " + hitId));
        hit.setStatus(status);
        hit.setReviewedBy(reviewerId);
        hit.setDismissedByAi(false);
        if (note != null && !note.isBlank()) {
            hit.setDismissalRationale(note);
        }
        return hitRepository.save(hit);
    }

    private String yearOf(String date) {
        if (date == null || date.length() < 4) return null;
        String candidate = date.substring(0, 4);
        return candidate.chars().allMatch(Character::isDigit) ? candidate : null;
    }
}
