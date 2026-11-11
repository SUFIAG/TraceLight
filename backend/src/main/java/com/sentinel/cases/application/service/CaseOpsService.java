package com.sentinel.cases.application.service;

import com.sentinel.alert.adapter.out.persistence.AlertRepository;
import com.sentinel.alert.domain.model.AlertStatus;
import com.sentinel.alert.domain.model.FraudAlert;
import com.sentinel.autonomy.adapter.out.persistence.AgentRunRepository;
import com.sentinel.cases.adapter.out.persistence.QaSampleRepository;
import com.sentinel.cases.domain.model.QaSample;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.mule.application.service.BeneficiaryRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Bulk operations and quality assurance.
 *
 * <p>Two capabilities an investigation team asks for immediately and almost
 * never gets:
 *
 * <ul>
 *   <li><b>Ring takedown.</b> When a network is confirmed, the response is
 *       one action across two hundred accounts, not two hundred actions.</li>
 *   <li><b>Quality sampling.</b> Random review of closed decisions — human and
 *       machine alike — because an automation programme without a measured
 *       error rate is a leap of faith.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseOpsService {

    private final AlertRepository alertRepository;
    private final QaSampleRepository qaSampleRepository;
    private final AgentRunRepository agentRunRepository;
    private final BeneficiaryRiskService beneficiaryRiskService;

    /** Applies one status to many alerts. Returns what changed and what did not. */
    @Transactional
    public Map<String, Object> bulkUpdateAlerts(UUID orgId, List<UUID> alertIds, AlertStatus status, UUID actorId) {
        int updated = 0;
        List<String> skipped = new ArrayList<>();

        for (UUID alertId : alertIds) {
            Optional<FraudAlert> maybe = alertRepository.findById(alertId)
                    .filter(a -> a.getOrganizationId().equals(orgId));
            if (maybe.isEmpty()) {
                skipped.add(alertId + ": not found in this organization");
                continue;
            }
            FraudAlert alert = maybe.get();
            alert.setStatus(status);
            alert.setReviewedBy(actorId);
            alert.setReviewedAt(Instant.now());
            alertRepository.save(alert);
            updated++;
        }

        log.info("Bulk alert update by {}: {} set to {}, {} skipped", actorId, updated, status, skipped.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", alertIds.size());
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("status", status.name());
        return result;
    }

    /**
     * Ring takedown: confirms every member beneficiary at once so the whole
     * network is blocked rather than its most visible member.
     */
    @Transactional
    public Map<String, Object> takedownRing(UUID orgId, List<String> members, String source) {
        int confirmed = 0;
        for (String member : members) {
            beneficiaryRiskService.confirm(orgId, member, source);
            confirmed++;
        }
        log.warn("Ring takedown by {}: {} beneficiaries confirmed", source, confirmed);
        return Map.of("members", members.size(), "confirmed", confirmed, "source", source);
    }

    /**
     * Draws a random sample for review, weighted towards agent decisions —
     * those carry the least human oversight and the most governance interest.
     */
    @Transactional
    public List<QaSample> drawSample(UUID orgId, int size) {
        List<QaSample> samples = new ArrayList<>();
        int agentShare = Math.max(1, size / 2);

        agentRunRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, agentShare * 3))
                .getContent().stream()
                .filter(run -> run.getHumanAgreed() == null)
                .limit(agentShare)
                .forEach(run -> samples.add(qaSampleRepository.save(QaSample.builder()
                        .organizationId(orgId)
                        .subjectType("AGENT_RUN")
                        .subjectId(run.getId())
                        .decidedByType("AGENT")
                        .originalDecision(run.getVerdict().name()
                                + (run.getProposedAction() == null ? "" : " / " + run.getProposedAction().name()))
                        .verdict("PENDING")
                        .findings(List.of())
                        .build())));

        alertRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        orgId, AlertStatus.RESOLVED, PageRequest.of(0, size - samples.size()))
                .getContent()
                .forEach(alert -> samples.add(qaSampleRepository.save(QaSample.builder()
                        .organizationId(orgId)
                        .subjectType("ALERT")
                        .subjectId(alert.getId())
                        .decidedByType("HUMAN")
                        .decidedBy(alert.getReviewedBy())
                        .originalDecision(alert.getStatus().name())
                        .verdict("PENDING")
                        .findings(List.of())
                        .build())));

        return samples;
    }

    @Transactional
    public QaSample review(UUID orgId, UUID sampleId, String verdict, List<String> findings,
                           String notes, UUID reviewerId) {
        QaSample sample = qaSampleRepository.findByIdAndOrganizationId(sampleId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("QaSample", sampleId.toString()));
        sample.setVerdict(verdict);
        sample.setFindings(findings == null ? List.of() : findings);
        sample.setReviewerNotes(notes);
        sample.setReviewedBy(reviewerId);
        sample.setReviewedAt(Instant.now());

        // A sampled agent decision found incorrect is also the agent's label.
        if ("AGENT_RUN".equals(sample.getSubjectType())) {
            agentRunRepository.findByIdAndOrganizationId(sample.getSubjectId(), orgId).ifPresent(run -> {
                run.setHumanAgreed("CORRECT".equals(verdict));
                run.setReviewedBy(reviewerId);
                agentRunRepository.save(run);
            });
        }
        return qaSampleRepository.save(sample);
    }

    /** Accuracy by decider type — the comparison that justifies widening autonomy. */
    @Transactional(readOnly = true)
    public Map<String, Object> qualityScorecard(UUID orgId) {
        Map<String, Map<String, Long>> byType = new LinkedHashMap<>();
        for (Object[] row : qaSampleRepository.accuracyBreakdown(orgId)) {
            String decider = String.valueOf(row[0]);
            String verdict = String.valueOf(row[1]);
            long count = ((Number) row[2]).longValue();
            byType.computeIfAbsent(decider, k -> new LinkedHashMap<>()).put(verdict, count);
        }

        Map<String, Object> scorecard = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : byType.entrySet()) {
            long correct = entry.getValue().getOrDefault("CORRECT", 0L);
            long total = entry.getValue().values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("reviewed", total);
            stats.put("correct", correct);
            stats.put("accuracy", total == 0 ? null : Math.round((double) correct / total * 1000) / 10.0);
            stats.put("breakdown", entry.getValue());
            scorecard.put(entry.getKey(), stats);
        }
        scorecard.put("pendingReview", qaSampleRepository.countByOrganizationIdAndVerdict(orgId, "PENDING"));
        return scorecard;
    }
}
