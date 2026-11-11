package com.sentinel.transaction.application.service;

import com.sentinel.audit.application.service.AuditService;
import com.sentinel.audit.domain.model.AuditAction;
import com.sentinel.audit.domain.model.EntityType;
import com.sentinel.common.config.CacheConfig;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.fraud.application.service.FraudAnalysisService;
import com.sentinel.transaction.application.dto.BulkUploadResponse;
import com.sentinel.transaction.application.dto.IngestTransactionRequest;
import com.sentinel.transaction.application.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CSV ingestion.
 *
 * <p>Deliberately a separate bean from {@link TransactionService}. Two reasons:
 *
 * <ol>
 *   <li><b>Fraud analysis.</b> A CSV upload that only stores rows is useless —
 *       every transaction stays PENDING and no alert is ever raised. Analysis
 *       requires {@link FraudAnalysisService}, which itself depends on
 *       {@code TransactionService}; running the upload from a third bean keeps
 *       that dependency acyclic.</li>
 *   <li><b>Transaction boundaries.</b> The previous implementation annotated the
 *       whole upload {@code @Transactional} and then called a {@code @Transactional}
 *       method on itself. Self-invocation bypasses the proxy, so every row shared
 *       one transaction: a single bad row marked it rollback-only and the entire
 *       upload was discarded at commit while still reporting success. Here each
 *       row crosses a proxy boundary and commits or fails on its own.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkIngestionService {

    /** Guards against a single upload consuming the request thread indefinitely. */
    private static final int MAX_ROWS = 25_000;

    /** How many row-level errors to return before truncating the list. */
    private static final int MAX_REPORTED_ERRORS = 50;

    private static final DateTimeFormatter[] ACCEPTED_DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
    };

    private final TransactionService transactionService;
    private final FraudAnalysisService fraudAnalysisService;
    private final AuditService auditService;

    /**
     * Parses, stores and scores a CSV upload.
     *
     * @param analyze when true every accepted row is put through the live fraud
     *                pipeline, producing risk scores, alerts and explanations
     */
    @CacheEvict(value = CacheConfig.DASHBOARD, key = "#organizationId")
    public BulkUploadResponse upload(MultipartFile file, UUID organizationId, boolean analyze) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "The uploaded file is empty");
        }

        List<String> errors = new ArrayList<>();
        List<UUID> accepted = new ArrayList<>();
        int rows = 0;

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setAllowMissingColumnNames(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                if (rows >= MAX_ROWS) {
                    errors.add("Upload truncated at " + MAX_ROWS
                            + " rows. Split the file and upload the remainder separately.");
                    break;
                }
                rows++;

                try {
                    IngestTransactionRequest request = toRequest(record);
                    TransactionResponse created = transactionService.ingestTransaction(
                            request, organizationId, idempotencyKeyFor(organizationId, request));
                    accepted.add(created.getId());
                } catch (Exception e) {
                    if (errors.size() < MAX_REPORTED_ERRORS) {
                        errors.add("Row " + record.getRecordNumber() + ": " + rootMessage(e));
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("CSV_PARSE_ERROR",
                    "Could not read the CSV file: " + rootMessage(e)
                            + ". Expected header: transactionExternalId,userId,merchantId,amount,"
                            + "currency,country,deviceId,ipAddress,timestamp");
        }

        int analyzed = 0;
        if (analyze) {
            // Chronological order matters: velocity, geographic and impossible-travel
            // rules all read the history that precedes each transaction, so scoring a
            // batch out of order would hide exactly the patterns the batch contains.
            for (UUID transactionId : accepted) {
                try {
                    fraudAnalysisService.analyzeTransaction(transactionId);
                    analyzed++;
                } catch (Exception e) {
                    log.warn("Analysis failed for uploaded transaction {}: {}",
                            transactionId, rootMessage(e));
                }
            }
        }

        auditService.logAction(organizationId, organizationId,
                AuditAction.CSV_UPLOADED, EntityType.TRANSACTION);

        log.info("CSV upload for org {}: {} rows read, {} accepted, {} rejected, {} analyzed",
                organizationId, rows, accepted.size(), rows - accepted.size(), analyzed);

        return BulkUploadResponse.builder()
                .totalProcessed(accepted.size())
                .totalFailed(rows - accepted.size())
                .totalAnalyzed(analyzed)
                .errors(errors.isEmpty() ? null : errors)
                .build();
    }

    private IngestTransactionRequest toRequest(CSVRecord record) {
        return IngestTransactionRequest.builder()
                .transactionExternalId(required(record, "transactionExternalId"))
                .userId(required(record, "userId"))
                .merchantId(required(record, "merchantId"))
                .amount(parseAmount(required(record, "amount")))
                .currency(required(record, "currency").toUpperCase())
                .country(optional(record, "country"))
                .deviceId(optional(record, "deviceId"))
                .ipAddress(optional(record, "ipAddress"))
                .timestamp(parseTimestamp(required(record, "timestamp")))
                .build();
    }

    /**
     * A deterministic key derived from the row's own identity rather than a random
     * UUID. Re-uploading the same file is then a no-op instead of a duplicate load,
     * which is the behaviour anyone retrying a failed upload expects.
     */
    private String idempotencyKeyFor(UUID organizationId, IngestTransactionRequest request) {
        return "csv:" + organizationId + ":" + request.getTransactionExternalId();
    }

    private String required(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            throw new IllegalArgumentException("missing required column '" + column + "'");
        }
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("column '" + column + "' is empty");
        }
        return value.trim();
    }

    private String optional(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private BigDecimal parseAmount(String raw) {
        // Tolerate thousands separators and currency symbols pasted in from a spreadsheet.
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid amount");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid amount");
        }
    }

    private Instant parseTimestamp(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            // Fall through to the local-time formats below.
        }
        for (DateTimeFormatter format : ACCEPTED_DATE_FORMATS) {
            try {
                return LocalDateTime.parse(raw, format).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format.
            }
        }
        throw new IllegalArgumentException(
                "'" + raw + "' is not a recognised timestamp. Use ISO-8601, for example 2026-09-28T14:05:00Z");
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return (message == null || message.isBlank()) ? cursor.getClass().getSimpleName() : message;
    }
}
