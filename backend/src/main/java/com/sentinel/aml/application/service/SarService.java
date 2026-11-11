package com.sentinel.aml.application.service;

import com.sentinel.aml.adapter.out.persistence.AmlFindingRepository;
import com.sentinel.aml.adapter.out.persistence.SarFilingRepository;
import com.sentinel.aml.domain.model.AmlFinding;
import com.sentinel.aml.domain.model.SarFiling;
import com.sentinel.aml.domain.model.SarStatus;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SAR lifecycle: draft, approve, file.
 *
 * <p>Two controls are enforced here rather than left to process discipline.
 * First, a report cannot be filed without an approving officer — the platform
 * drafts, a human files. Second, the approver and the drafter are recorded
 * separately, so the audit trail shows who took responsibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SarService {

    /** Most regimes require filing within 30 days of detection. */
    private static final int FILING_DEADLINE_DAYS = 30;

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final SarFilingRepository sarRepository;
    private final AmlFindingRepository findingRepository;
    private final SarNarrativeService narrativeService;

    @Transactional
    public SarFiling draft(UUID orgId, String subjectId, String subjectName,
                           String jurisdiction, List<UUID> findingIds) {
        List<AmlFinding> findings = findingIds == null || findingIds.isEmpty()
                ? findingRepository.findByOrganizationIdAndSubjectIdOrderByCreatedAtDesc(orgId, subjectId)
                : findingIds.stream()
                .map(id -> findingRepository.findByIdAndOrganizationId(id, orgId)
                        .orElseThrow(() -> new ResourceNotFoundException("AmlFinding", id.toString())))
                .toList();

        if (findings.isEmpty()) {
            throw new BusinessException("NO_FINDINGS",
                    "Cannot draft a report for " + subjectId + ": no monitoring findings exist");
        }

        SarNarrativeService.Draft draft =
                narrativeService.draft(subjectId, subjectName, jurisdiction, findings);

        BigDecimal total = findings.stream()
                .map(f -> f.getTotalValue() == null ? BigDecimal.ZERO : f.getTotalValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SarFiling filing = sarRepository.save(SarFiling.builder()
                .organizationId(orgId)
                .referenceNumber(nextReference())
                .subjectId(subjectId)
                .subjectName(subjectName)
                .jurisdiction(jurisdiction)
                .status(SarStatus.PENDING_APPROVAL)
                .narrative(draft.narrative())
                .findingIds(findings.stream().map(f -> f.getId().toString()).toList())
                .typologies(findings.stream().map(f -> f.getTypology().name()).distinct().toList())
                .totalValue(total)
                .activityStart(findings.stream().map(AmlFinding::getWindowStart)
                        .filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null))
                .activityEnd(findings.stream().map(AmlFinding::getWindowEnd)
                        .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null))
                .aiDrafted(draft.aiDrafted())
                .draftedAt(Instant.now())
                .deadlineAt(Instant.now().plus(FILING_DEADLINE_DAYS, ChronoUnit.DAYS))
                .build());

        findings.forEach(f -> {
            f.setStatus("ESCALATED");
            findingRepository.save(f);
        });

        log.info("Drafted {} for {} covering {} findings (aiDrafted={})",
                filing.getReferenceNumber(), subjectId, findings.size(), draft.aiDrafted());
        return filing;
    }

    @Transactional
    public SarFiling updateNarrative(UUID orgId, UUID filingId, String narrative) {
        SarFiling filing = load(orgId, filingId);
        if (filing.getStatus() == SarStatus.FILED) {
            throw new BusinessException("ALREADY_FILED", "A filed report cannot be edited");
        }
        filing.setNarrative(narrative);
        // Human edits mean the narrative is no longer purely machine drafted.
        filing.setAiDrafted(false);
        return sarRepository.save(filing);
    }

    @Transactional
    public SarFiling approve(UUID orgId, UUID filingId, UUID approverId) {
        SarFiling filing = load(orgId, filingId);
        if (filing.getStatus() != SarStatus.PENDING_APPROVAL && filing.getStatus() != SarStatus.DRAFT) {
            throw new BusinessException("INVALID_STATE",
                    "Only a draft or pending report can be approved, not one in state " + filing.getStatus());
        }
        filing.setStatus(SarStatus.APPROVED);
        filing.setApprovedBy(approverId);
        filing.setApprovedAt(Instant.now());
        return sarRepository.save(filing);
    }

    @Transactional
    public SarFiling file(UUID orgId, UUID filingId) {
        SarFiling filing = load(orgId, filingId);
        if (filing.getStatus() != SarStatus.APPROVED) {
            throw new BusinessException("NOT_APPROVED",
                    "A report must be approved by a compliance officer before it is filed");
        }
        filing.setStatus(SarStatus.FILED);
        filing.setFiledAt(Instant.now());

        filing.getFindingIds().forEach(id -> findingRepository
                .findByIdAndOrganizationId(UUID.fromString(id), orgId)
                .ifPresent(finding -> {
                    finding.setStatus("SAR_FILED");
                    findingRepository.save(finding);
                }));

        log.info("Filed {} with {}", filing.getReferenceNumber(), filing.getJurisdiction());
        return sarRepository.save(filing);
    }

    @Transactional
    public SarFiling reject(UUID orgId, UUID filingId, UUID reviewerId) {
        SarFiling filing = load(orgId, filingId);
        filing.setStatus(SarStatus.REJECTED);
        filing.setApprovedBy(reviewerId);
        filing.setApprovedAt(Instant.now());
        return sarRepository.save(filing);
    }

    /** Reports whose regulatory clock is running out. */
    @Transactional(readOnly = true)
    public List<SarFiling> overdue(UUID orgId) {
        return sarRepository.findByOrganizationIdAndDeadlineAtBeforeAndStatusNot(
                orgId, Instant.now(), SarStatus.FILED);
    }

    private SarFiling load(UUID orgId, UUID filingId) {
        return sarRepository.findByIdAndOrganizationId(filingId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("SarFiling", filingId.toString()));
    }

    private String nextReference() {
        return "SAR-" + REFERENCE_DATE.format(Instant.now()) + "-"
                + String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
    }
}
