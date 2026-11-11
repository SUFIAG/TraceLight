package com.sentinel.cases.application.service;

import com.sentinel.alert.application.dto.AlertResponse;
import com.sentinel.alert.application.service.AlertService;
import com.sentinel.audit.application.service.AuditService;
import com.sentinel.auth.adapter.out.persistence.UserRepository;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.cases.adapter.out.persistence.CaseHistoryRepository;
import com.sentinel.cases.adapter.out.persistence.FraudCaseRepository;
import com.sentinel.cases.application.dto.AssignCaseRequest;
import com.sentinel.cases.application.dto.CaseHistoryResponse;
import com.sentinel.cases.application.dto.CaseResponse;
import com.sentinel.cases.application.dto.CreateCaseRequest;
import com.sentinel.cases.application.dto.UpdateCaseStatusRequest;
import com.sentinel.cases.domain.model.CaseHistory;
import com.sentinel.cases.domain.model.CasePriority;
import com.sentinel.cases.domain.model.CaseResolution;
import com.sentinel.cases.domain.model.CaseStatus;
import com.sentinel.cases.domain.model.FraudCase;
import com.sentinel.cases.domain.service.WorkflowEngine;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.common.util.Enums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the fraud case lifecycle: creation from alerts, workflow transitions,
 * assignment and history tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private final FraudCaseRepository fraudCaseRepository;
    private final CaseHistoryRepository caseHistoryRepository;
    private final AlertService alertService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final WorkflowEngine workflowEngine;
    private final UserRepository userRepository;

    @Transactional
    public CaseResponse createFromAlert(UUID organizationId, UUID createdBy, CreateCaseRequest request) {
        if (fraudCaseRepository.existsByAlertId(request.getAlertId())) {
            throw new BusinessException("CASE_EXISTS",
                    "A case already exists for alert " + request.getAlertId());
        }

        AlertResponse alert = alertService.findById(request.getAlertId(), organizationId);

        CasePriority priority = request.getPriority() != null && !request.getPriority().isBlank()
                ? Enums.parse(CasePriority.class, request.getPriority(), "priority")
                : mapPriority(alert.getSeverity());

        String title = request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle()
                : "Investigation for alert " + alert.getId();

        FraudCase fraudCase = FraudCase.builder()
                .organizationId(organizationId)
                .alertId(alert.getId())
                .title(title)
                .priority(priority)
                .status(CaseStatus.OPEN)
                .createdBy(createdBy)
                .build();

        FraudCase saved = fraudCaseRepository.save(fraudCase);
        recordHistory(saved.getId(), "CREATED", null, CaseStatus.OPEN.name(), createdBy);
        auditService.logAction(organizationId, saved.getId(), AuditAction.CASE_CREATED, EntityType.CASE);
        log.info("Case {} created from alert {} (priority={})", saved.getId(), alert.getId(), priority);
        return CaseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> list(UUID organizationId, String status, UUID assignedTo, Pageable pageable) {
        Page<FraudCase> page;
        if (assignedTo != null) {
            page = fraudCaseRepository.findByOrganizationIdAndAssignedToOrderByCreatedAtDesc(
                    organizationId, assignedTo, pageable);
        } else if (status != null && !status.isBlank()) {
            page = fraudCaseRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                    organizationId, Enums.parse(CaseStatus.class, status, "status"), pageable);
        } else {
            page = fraudCaseRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        }
        return page.map(CaseResponse::from);
    }

    @Transactional(readOnly = true)
    public CaseResponse getById(UUID organizationId, UUID caseId) {
        return CaseResponse.from(loadOwned(organizationId, caseId));
    }

    @Transactional
    public CaseResponse changeStatus(UUID organizationId, UUID userId, UUID caseId,
                                     UpdateCaseStatusRequest request) {
        FraudCase fraudCase = loadOwned(organizationId, caseId);
        CaseStatus oldStatus = fraudCase.getStatus();
        CaseStatus newStatus = Enums.parse(CaseStatus.class, request.getStatus(), "status");

        workflowEngine.validateTransition(oldStatus, newStatus);

        if (newStatus == CaseStatus.RESOLVED) {
            if (request.getResolution() == null || request.getResolution().isBlank()) {
                throw new BusinessException("RESOLUTION_REQUIRED",
                        "A resolution is required to resolve a case");
            }
            fraudCase.setResolution(Enums.parse(CaseResolution.class, request.getResolution(), "resolution"));
            fraudCase.setResolvedAt(Instant.now());
        }

        fraudCase.setStatus(newStatus);
        FraudCase saved = fraudCaseRepository.save(fraudCase);

        recordHistory(caseId, "STATUS_CHANGED", oldStatus.name(), newStatus.name(), userId);
        auditService.logAction(organizationId, caseId,
                newStatus == CaseStatus.RESOLVED ? AuditAction.CASE_RESOLVED : AuditAction.CASE_UPDATED,
                EntityType.CASE);
        notificationService.notifyStatusChange(saved, oldStatus, newStatus);
        return CaseResponse.from(saved);
    }

    @Transactional
    public CaseResponse assign(UUID organizationId, UUID userId, UUID caseId, AssignCaseRequest request) {
        FraudCase fraudCase = loadOwned(organizationId, caseId);

        // The assignee id arrives from the client, so it must be a member of this
        // organization. Without this check a case could be handed to a user in
        // another tenant.
        userRepository.findById(request.getAssigneeId())
                .filter(user -> user.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException("INVALID_ASSIGNEE",
                        "The assignee is not a member of this organization"));

        UUID oldAssignee = fraudCase.getAssignedTo();
        fraudCase.setAssignedTo(request.getAssigneeId());

        // Auto-advance an untouched OPEN case into INVESTIGATING on first assignment.
        if (fraudCase.getStatus() == CaseStatus.OPEN) {
            fraudCase.setStatus(CaseStatus.INVESTIGATING);
            recordHistory(caseId, "STATUS_CHANGED", CaseStatus.OPEN.name(),
                    CaseStatus.INVESTIGATING.name(), userId);
        }

        FraudCase saved = fraudCaseRepository.save(fraudCase);
        recordHistory(caseId, "ASSIGNED",
                oldAssignee != null ? oldAssignee.toString() : null,
                request.getAssigneeId().toString(), userId);
        auditService.logAction(organizationId, caseId, AuditAction.CASE_ASSIGNED, EntityType.CASE);
        notificationService.notifyAssignment(saved, request.getAssigneeId());
        return CaseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseHistoryResponse> getHistory(UUID organizationId, UUID caseId) {
        loadOwned(organizationId, caseId);
        return caseHistoryRepository.findByCaseIdOrderByPerformedAtAsc(caseId)
                .stream().map(CaseHistoryResponse::from).toList();
    }

    private FraudCase loadOwned(UUID organizationId, UUID caseId) {
        FraudCase fraudCase = fraudCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId.toString()));
        if (!fraudCase.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Case", caseId.toString());
        }
        return fraudCase;
    }

    private void recordHistory(UUID caseId, String action, String oldValue, String newValue, UUID performedBy) {
        caseHistoryRepository.save(CaseHistory.builder()
                .caseId(caseId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(performedBy)
                .build());
    }

    private CasePriority mapPriority(String severity) {
        if (severity == null) return CasePriority.MEDIUM;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> CasePriority.CRITICAL;
            case "HIGH" -> CasePriority.HIGH;
            case "LOW" -> CasePriority.LOW;
            default -> CasePriority.MEDIUM;
        };
    }
}
