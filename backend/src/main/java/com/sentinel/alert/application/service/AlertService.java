package com.sentinel.alert.application.service;

import com.sentinel.alert.adapter.out.persistence.AlertRepository;
import com.sentinel.alert.application.dto.AlertResponse;
import com.sentinel.alert.domain.model.AlertSeverity;
import com.sentinel.alert.domain.model.AlertStatus;
import com.sentinel.alert.domain.model.FraudAlert;
import com.sentinel.audit.application.service.AuditService;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.common.util.Enums;
import com.sentinel.fraud.risk.domain.model.RiskLevel;
import com.sentinel.fraud.risk.domain.model.RiskScore;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private static final int ALERT_THRESHOLD = 60;
    private final AlertRepository alertRepository;
    private final AuditService auditService;

    @Transactional
    public FraudAlert createAlertIfNeeded(Transaction transaction, RiskScore riskScore,
                                           List<RuleResult> triggeredRules) {
        if (riskScore.getScore() < ALERT_THRESHOLD) {
            return null;
        }

        AlertSeverity severity = mapSeverity(riskScore.getLevel());
        List<String> ruleNames = triggeredRules.stream().map(RuleResult::getRuleName).toList();
        String reason = buildReason(triggeredRules, riskScore);

        FraudAlert alert = FraudAlert.builder()
                .organizationId(transaction.getOrganizationId())
                .transactionId(transaction.getId())
                .severity(severity)
                .status(AlertStatus.OPEN)
                .reason(reason)
                .triggeredRules(ruleNames)
                .build();

        FraudAlert saved = alertRepository.save(alert);
        auditService.logAction(transaction.getOrganizationId(), saved.getId(),
                AuditAction.ALERT_CREATED, EntityType.ALERT);
        log.info("Alert created: {} severity={} score={} for txn {}",
                saved.getId(), severity, riskScore.getScore(), transaction.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> findByOrganization(UUID organizationId, String status, Pageable pageable) {
        Page<FraudAlert> page;
        if (status != null && !status.isBlank()) {
            page = alertRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                    organizationId, Enums.parse(AlertStatus.class, status, "status"), pageable);
        } else {
            page = alertRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        }
        return page.map(AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public AlertResponse findById(UUID alertId, UUID organizationId) {
        FraudAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId.toString()));
        if (!alert.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Alert", alertId.toString());
        }
        return AlertResponse.from(alert);
    }

    @Transactional
    public AlertResponse updateStatus(UUID alertId, UUID organizationId, String newStatus, UUID reviewerId) {
        FraudAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId.toString()));
        if (!alert.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Alert", alertId.toString());
        }

        AlertStatus status = Enums.parse(AlertStatus.class, newStatus, "status");
        alert.setStatus(status);
        alert.setReviewedBy(reviewerId);
        alert.setReviewedAt(Instant.now());

        FraudAlert saved = alertRepository.save(alert);

        AuditAction action = status == AlertStatus.FALSE_POSITIVE
                ? AuditAction.ALERT_FALSE_POSITIVE
                : status == AlertStatus.RESOLVED ? AuditAction.ALERT_RESOLVED : AuditAction.ALERT_REVIEWED;
        auditService.logAction(organizationId, saved.getId(), action, EntityType.ALERT);

        return AlertResponse.from(saved);
    }

    private AlertSeverity mapSeverity(RiskLevel level) {
        return switch (level) {
            case CRITICAL -> AlertSeverity.CRITICAL;
            case HIGH -> AlertSeverity.HIGH;
            default -> AlertSeverity.MEDIUM;
        };
    }

    private String buildReason(List<RuleResult> triggeredRules, RiskScore riskScore) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Risk Score: %d (%s). ", riskScore.getScore(), riskScore.getLevel()));
        sb.append("Triggered rules: ");
        triggeredRules.forEach(r -> sb.append(String.format("[%s: %s] ", r.getRuleName(), r.getReason())));
        return sb.toString().trim();
    }
}

