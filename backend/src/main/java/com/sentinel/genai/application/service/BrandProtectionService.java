package com.sentinel.genai.application.service;

import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.genai.adapter.out.persistence.PhishingDomainRepository;
import com.sentinel.genai.domain.model.PhishingDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Detects domains impersonating the institution's brand.
 *
 * <p>Three techniques cover almost all real impersonation:
 *
 * <ul>
 *   <li><b>Homoglyph</b> — characters that render alike: rn for m, 1 for l,
 *       Cyrillic а for Latin a. Invisible to a customer reading quickly.</li>
 *   <li><b>Typosquat</b> — one edit away from the real domain, catching
 *       mistyped addresses.</li>
 *   <li><b>Combosquat</b> — the real brand plus a plausible word:
 *       secure-, -login, -verify. The most common and the most effective,
 *       because the brand name is spelled perfectly.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandProtectionService {

    /** Characters that render alike at a glance, normalised before comparison. */
    private static final Map<Character, Character> HOMOGLYPHS = Map.of(
            '0', 'o', '1', 'l', '3', 'e', '4', 'a', '5', 's', '7', 't', '8', 'b', '9', 'g');

    /** Words attackers prepend or append to a correctly spelled brand. */
    private static final List<String> COMBO_TERMS = List.of(
            "secure", "login", "verify", "account", "support", "update", "online",
            "my", "web", "app", "auth", "signin", "help", "service", "alert");

    private final PhishingDomainRepository repository;

    /**
     * Assesses candidate domains against the protected brand terms.
     *
     * @param candidates domains from a registration feed, certificate
     *                   transparency log, or passive DNS
     */
    @Transactional
    public List<PhishingDomain> assess(UUID orgId, List<String> brandTerms, List<String> candidates) {
        List<PhishingDomain> flagged = new ArrayList<>();

        for (String candidate : candidates) {
            String domain = candidate.toLowerCase(Locale.ROOT).trim();
            String label = labelOf(domain);

            for (String brand : brandTerms) {
                String brandTerm = brand.toLowerCase(Locale.ROOT).trim();
                if (label.equals(brandTerm)) continue;

                Assessment assessment = classify(label, brandTerm);
                if (assessment == null) continue;

                flagged.add(upsert(orgId, domain, brandTerm, assessment));
                break;
            }
        }

        log.info("Brand protection assessed {} candidates against {} terms: {} flagged",
                candidates.size(), brandTerms.size(), flagged.size());
        return flagged;
    }

    private Assessment classify(String label, String brand) {
        if (label.contains(brand)) {
            for (String term : COMBO_TERMS) {
                if (label.contains(term)) {
                    return new Assessment("COMBOSQUAT", 90,
                            List.of("Contains the brand term '" + brand + "' alongside '" + term + "'"));
                }
            }
            return new Assessment("COMBOSQUAT", 75,
                    List.of("Contains the brand term '" + brand + "' with additional words"));
        }

        String normalised = normaliseHomoglyphs(label);
        String normalisedBrand = normaliseHomoglyphs(brand);
        if (normalised.contains(normalisedBrand) || normalised.equals(normalisedBrand)) {
            return new Assessment("HOMOGLYPH", 95,
                    List.of("Renders as '" + brand + "' once lookalike characters are normalised"));
        }

        int distance = levenshtein(label, brand);
        if (distance <= 2 && Math.abs(label.length() - brand.length()) <= 2) {
            return new Assessment("TYPOSQUAT", distance == 1 ? 92 : 80,
                    List.of(distance + " character(s) away from '" + brand + "'"));
        }

        return null;
    }

    private PhishingDomain upsert(UUID orgId, String domain, String brand, Assessment assessment) {
        PhishingDomain record = repository.findByOrganizationIdAndDomain(orgId, domain)
                .orElseGet(() -> PhishingDomain.builder()
                        .organizationId(orgId)
                        .domain(domain)
                        .status("DETECTED")
                        .hasLoginForm(false)
                        .sslIssued(false)
                        .build());

        record.setBrandTerm(brand);
        record.setTechnique(assessment.technique());
        record.setSimilarityScore(assessment.score());
        record.setEvidence(assessment.evidence());
        if (record.getRegisteredAt() == null) record.setRegisteredAt(Instant.now());
        return repository.save(record);
    }

    /** Records what reconnaissance found on a flagged domain. */
    @Transactional
    public PhishingDomain enrich(UUID orgId, UUID id, boolean hasLoginForm, boolean sslIssued, String ip) {
        PhishingDomain record = repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingDomain", id.toString()));
        record.setHasLoginForm(hasLoginForm);
        record.setSslIssued(sslIssued);
        record.setResolvesToIp(ip);

        List<String> evidence = new ArrayList<>(record.getEvidence());
        if (hasLoginForm) {
            // A parked squat is a nuisance; a live credential form is an attack.
            evidence.add("Serving a credential form — actively phishing");
            record.setSimilarityScore(Math.min(100, record.getSimilarityScore() + 10));
        }
        if (sslIssued) {
            evidence.add("Holds a valid certificate, so the browser shows no warning");
        }
        record.setEvidence(evidence);
        return repository.save(record);
    }

    @Transactional
    public PhishingDomain updateStatus(UUID orgId, UUID id, String status) {
        PhishingDomain record = repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingDomain", id.toString()));
        record.setStatus(status);
        if ("TAKEDOWN_REQUESTED".equals(status)) {
            record.setTakedownRequestedAt(Instant.now());
        }
        if ("TAKEN_DOWN".equals(status) || "DISMISSED".equals(status)) {
            record.setResolvedAt(Instant.now());
        }
        return repository.save(record);
    }

    /** The registrable label, without subdomains or the suffix. */
    private String labelOf(String domain) {
        String withoutScheme = domain.replaceFirst("^https?://", "").split("/")[0];
        String[] parts = withoutScheme.split("\\.");
        if (parts.length <= 1) return withoutScheme;
        return parts[parts.length - 2];
    }

    private String normaliseHomoglyphs(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            sb.append(HOMOGLYPHS.getOrDefault(c, c));
        }
        // "rn" renders almost identically to "m" at normal reading size.
        return sb.toString().replace("rn", "m").replace("vv", "w");
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

    private record Assessment(String technique, int score, List<String> evidence) {
    }
}
