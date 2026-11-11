package com.sentinel.audit.application.service;

import com.sentinel.audit.adapter.out.persistence.AuditRepository;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.AuditEntry;
import com.sentinel.audit.domain.model.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the immutable audit trail.
 *
 * An audit write must never fail the business operation that triggered it — a
 * login is not rejected because its audit row could not be stored. Achieving
 * that takes more than a try/catch around a repository call:
 *
 *   - A plain save() defers the INSERT to commit, which runs after the method
 *     body returns, so a catch inside the method never sees the failure.
 *   - Flushing inside the try surfaces the failure in the right place, but a
 *     failed flush marks the transaction rollback-only, and the subsequent
 *     commit throws UnexpectedRollbackException into the caller anyway.
 *
 * So the transaction is driven programmatically here. The template owns the
 * full begin/flush/commit cycle, and every outcome lands inside this method's
 * catch rather than escaping to whoever called it.
 */
@Service
@Slf4j
public class AuditService {

    private final AuditRepository auditRepository;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditRepository auditRepository, PlatformTransactionManager transactionManager) {
        this.auditRepository = auditRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // A separate transaction, so an audit failure cannot poison the caller's.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void logAction(UUID organizationId, UUID entityId, AuditAction action, EntityType entityType) {
        logAction(organizationId, null, entityId, action, entityType, null, null);
    }

    public void logAction(UUID organizationId, UUID userId, UUID entityId,
                          AuditAction action, EntityType entityType,
                          Map<String, Object> decisionFactors, String reasoning) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                AuditEntry entry = AuditEntry.builder()
                        .organizationId(organizationId)
                        .userId(userId)
                        .action(action.name())
                        .entityType(entityType.name())
                        .entityId(entityId)
                        .decisionFactors(decisionFactors)
                        .reasoning(reasoning)
                        .result("SUCCESS")
                        .timestamp(Instant.now())
                        .build();
                auditRepository.save(entry);
            });
        } catch (Exception e) {
            log.error("Failed to create audit entry: action={}, entityId={}", action, entityId, e);
        }
    }
}
