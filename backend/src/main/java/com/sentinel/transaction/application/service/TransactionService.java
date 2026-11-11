package com.sentinel.transaction.application.service;

import com.sentinel.audit.application.service.AuditService;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.transaction.adapter.out.persistence.IdempotencyKeyRepository;
import com.sentinel.common.util.Enums;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.application.dto.IngestTransactionRequest;
import com.sentinel.transaction.application.dto.TransactionResponse;
import com.sentinel.transaction.domain.model.IdempotencyKey;
import com.sentinel.transaction.domain.model.Transaction;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditService auditService;

    @Transactional
    public TransactionResponse ingestTransaction(IngestTransactionRequest request,
                                                  UUID organizationId,
                                                  String idempotencyKey) {
        var existing = idempotencyKeyRepository.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for key: {}", idempotencyKey);
            Transaction txn = transactionRepository.findById(existing.get().getTransactionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", existing.get().getTransactionId().toString()));
            return TransactionResponse.from(txn);
        }

        validateTransaction(request);

        Transaction transaction = Transaction.builder()
                .organizationId(organizationId)
                .transactionExternalId(request.getTransactionExternalId())
                .userId(request.getUserId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .country(request.getCountry())
                .deviceId(request.getDeviceId())
                .ipAddress(request.getIpAddress())
                .status(TransactionStatus.PENDING)
                .timestamp(request.getTimestamp())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        IdempotencyKey key = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .transactionId(saved.getId())
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();
        idempotencyKeyRepository.save(key);

        auditService.logAction(organizationId, saved.getId(), AuditAction.TRANSACTION_INGESTED, EntityType.TRANSACTION);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findByOrganization(UUID organizationId, String status, Pageable pageable) {
        Page<Transaction> page;
        if (status != null && !status.isBlank()) {
            page = transactionRepository.findByOrganizationIdAndStatus(
                    organizationId, Enums.parse(TransactionStatus.class, status, "status"), pageable);
        } else {
            page = transactionRepository.findByOrganizationId(organizationId, pageable);
        }
        return page.map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID id, UUID organizationId) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id.toString()));
        if (!txn.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Transaction", id.toString());
        }
        return TransactionResponse.from(txn);
    }

    @Transactional
    public void updateStatus(UUID transactionId, TransactionStatus status) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId.toString()));
        txn.setStatus(status);
        transactionRepository.save(txn);
    }

    private void validateTransaction(IngestTransactionRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Amount must be positive");
        }
        if (!isValidCurrency(request.getCurrency())) {
            throw new BusinessException("INVALID_CURRENCY", "Invalid currency code: " + request.getCurrency());
        }
        if (request.getTimestamp() != null && request.getTimestamp().isAfter(Instant.now().plusSeconds(60))) {
            throw new BusinessException("FUTURE_TIMESTAMP", "Timestamp cannot be in the future");
        }
    }

    private boolean isValidCurrency(String currency) {
        try {
            Currency.getInstance(currency);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
