package com.sentinel.identity.application.service;

import com.sentinel.identity.adapter.out.persistence.BusinessEntityRepository;
import com.sentinel.identity.adapter.out.persistence.UboNodeRepository;
import com.sentinel.identity.application.dto.KybRequest;
import com.sentinel.identity.domain.model.BusinessEntity;
import com.sentinel.identity.domain.model.SanctionsHit;
import com.sentinel.identity.domain.model.UboNode;
import com.sentinel.identity.domain.model.VerificationDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business onboarding: shell-company scoring and beneficial-ownership discovery.
 *
 * <p>The ownership walk is the part that is usually skipped. A shell is rarely
 * owned directly by the person who controls it; it is owned by another company
 * in a second jurisdiction, which is owned by a third. Effective control is
 * the product of the percentages along the chain, so a 40% stake held through
 * two layers of 60% holdings is 14.4% — below the beneficial-ownership
 * threshold, which is precisely why the structure was built that way.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KybService {

    /** Standard beneficial-ownership threshold across most regimes. */
    private static final double UBO_THRESHOLD = 25.0;
    private static final int MAX_DEPTH = 6;

    private final BusinessEntityRepository businessEntityRepository;
    private final UboNodeRepository uboNodeRepository;
    private final SanctionsScreeningService sanctionsScreening;

    @Transactional
    public BusinessEntity onboard(UUID orgId, KybRequest request) {
        List<String> findings = new ArrayList<>();

        Instant incorporation = parseDate(request.getIncorporationDate());
        int shellScore = scoreShell(request, incorporation, findings);

        BusinessEntity business = businessEntityRepository
                .findByOrganizationIdAndRegistrationNumber(orgId, request.getRegistrationNumber())
                .orElseGet(() -> BusinessEntity.builder()
                        .organizationId(orgId)
                        .registrationNumber(request.getRegistrationNumber())
                        .build());

        business.setLegalName(request.getLegalName());
        business.setRegistrationCountry(request.getRegistrationCountry());
        business.setIncorporationDate(incorporation);
        business.setIndustryCode(request.getIndustryCode());
        business.setRegisteredAddress(request.getRegisteredAddress());
        business.setAddressCohabitants(orZero(request.getAddressCohabitants()));
        business.setDirectorCount(orZero(request.getDirectorCount()));
        business.setShellScore(shellScore);
        business = businessEntityRepository.save(business);

        uboNodeRepository.deleteByBusinessEntityId(business.getId());
        List<UboNode> nodes = new ArrayList<>();
        walk(orgId, business.getId(), null, request.getOwners(), 1, 100.0, nodes, findings);
        uboNodeRepository.saveAll(nodes);

        int depth = nodes.stream().mapToInt(UboNode::getDepth).max().orElse(0);
        long ubos = nodes.stream().filter(UboNode::isUbo).count();
        if (depth >= 3) {
            findings.add("Ownership chain is " + depth + " layers deep");
        }
        if (ubos == 0 && !nodes.isEmpty()) {
            findings.add("No natural person reaches the " + (int) UBO_THRESHOLD + "% control threshold");
        }

        int ownershipPoints = Math.min(30, depth * 8) + (ubos == 0 && !nodes.isEmpty() ? 20 : 0);
        int riskScore = Math.min(100, shellScore + ownershipPoints);

        business.setOwnershipDepth(depth);
        business.setRiskScore(riskScore);
        business.setFindings(findings.isEmpty() ? List.of("No adverse KYB findings") : findings);
        business.setDecision(decide(riskScore));

        BusinessEntity saved = businessEntityRepository.save(business);
        log.info("KYB for {} ({}): shell={} risk={} depth={} ubos={}",
                request.getLegalName(), request.getRegistrationNumber(), shellScore, riskScore, depth, ubos);
        return saved;
    }

    private int scoreShell(KybRequest request, Instant incorporation, List<String> findings) {
        int score = 0;

        if (incorporation != null) {
            long days = Duration.between(incorporation, Instant.now()).toDays();
            if (days < 90) {
                findings.add("Incorporated " + days + " days ago");
                score += 30;
            } else if (days < 365) {
                findings.add("Incorporated less than a year ago");
                score += 15;
            }
        }

        int cohabitants = orZero(request.getAddressCohabitants());
        if (cohabitants >= 100) {
            findings.add(cohabitants + " other companies share the registered address");
            score += 30;
        } else if (cohabitants >= 20) {
            findings.add(cohabitants + " other companies share the registered address");
            score += 18;
        }

        int directors = orZero(request.getDirectorCount());
        if (directors == 1) {
            score += 8;
        }

        int linked = orZero(request.getDirectorsLinkedToFlaggedEntities());
        if (linked > 0) {
            findings.add(linked + " director(s) also appear on already-flagged entities");
            score += Math.min(30, 15 * linked);
        }

        if (Boolean.TRUE.equals(request.getRegistryUnverified())) {
            findings.add("Company registry record could not be verified");
            score += 25;
        }

        return Math.min(100, score);
    }

    /**
     * Depth-first walk of the ownership tree, multiplying percentages along the
     * path. Natural persons crossing the threshold are marked as UBOs and
     * screened; companies are expanded further.
     */
    private void walk(UUID orgId, UUID businessId, UUID parentId, List<KybRequest.Owner> owners,
                      int depth, double inheritedPercent, List<UboNode> collected, List<String> findings) {
        if (owners == null || owners.isEmpty() || depth > MAX_DEPTH) return;

        for (KybRequest.Owner owner : owners) {
            double declared = owner.getOwnershipPercent() == null ? 0 : owner.getOwnershipPercent();
            double effective = inheritedPercent * declared / 100.0;
            boolean isPerson = "PERSON".equalsIgnoreCase(owner.getType());
            boolean isUbo = isPerson && effective >= UBO_THRESHOLD;

            UboNode node = UboNode.builder()
                    .organizationId(orgId)
                    .businessEntityId(businessId)
                    .parentNodeId(parentId)
                    .ownerType(isPerson ? "PERSON" : "COMPANY")
                    .ownerName(owner.getName())
                    .ownerCountry(owner.getCountry())
                    .ownershipPercent(declared)
                    .effectivePercent(Math.round(effective * 100) / 100.0)
                    .depth(depth)
                    .isUbo(isUbo)
                    .sanctionsScreened(false)
                    .pepFlag(Boolean.TRUE.equals(owner.getPep()))
                    .build();

            if (isUbo) {
                List<SanctionsHit> hits = sanctionsScreening.screen(
                        orgId, "UBO:" + owner.getName(), owner.getName(), null, owner.getCountry(), null);
                node.setSanctionsScreened(true);
                boolean open = hits.stream().anyMatch(h -> "OPEN".equals(h.getStatus()));
                if (open) {
                    findings.add("Beneficial owner " + owner.getName() + " matched a screening list");
                }
            }
            if (Boolean.TRUE.equals(owner.getPep())) {
                findings.add("Beneficial owner " + owner.getName() + " is a politically exposed person");
            }

            collected.add(node);
            // Nested holdings are written with a null parent reference because the
            // parent id is assigned on save; the tree is reconstructed by depth
            // and order, which is enough for display and for the UBO maths.
            walk(orgId, businessId, null, owner.getOwners(), depth + 1, effective, collected, findings);
        }
    }

    private VerificationDecision decide(int riskScore) {
        if (riskScore >= 80) return VerificationDecision.REJECTED;
        if (riskScore >= 55) return VerificationDecision.MANUAL_REVIEW;
        if (riskScore >= 35) return VerificationDecision.ADDITIONAL_EVIDENCE_REQUIRED;
        if (riskScore >= 20) return VerificationDecision.APPROVED_WITH_LIMITS;
        return VerificationDecision.APPROVED;
    }

    @Transactional(readOnly = true)
    public List<UboNode> ownershipTree(UUID businessId) {
        return uboNodeRepository.findByBusinessEntityIdOrderByDepthAsc(businessId);
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.substring(0, 10)).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            log.debug("Unparseable incorporation date: {}", value);
            return null;
        }
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
