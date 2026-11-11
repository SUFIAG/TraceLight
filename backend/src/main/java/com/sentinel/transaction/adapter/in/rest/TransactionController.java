package com.sentinel.transaction.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.transaction.application.dto.BulkUploadResponse;
import com.sentinel.transaction.application.dto.IngestTransactionRequest;
import com.sentinel.transaction.application.dto.TransactionResponse;
import com.sentinel.transaction.application.service.BulkIngestionService;
import com.sentinel.transaction.application.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final BulkIngestionService bulkIngestionService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> ingestTransaction(
            @RequestBody @Valid IngestTransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        UUID orgId = extractOrgId(auth);
        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        TransactionResponse response = transactionService.ingestTransaction(request, orgId, key);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Bulk CSV ingestion.
     *
     * @param analyze run every accepted row through the fraud pipeline. On by
     *                default, because an upload that produces no risk scores and
     *                no alerts leaves the platform looking empty.
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<BulkUploadResponse>> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean analyze,
            Authentication auth) {
        UUID orgId = extractOrgId(auth);
        BulkUploadResponse response = bulkIngestionService.upload(file, orgId, analyze);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> listTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication auth) {
        UUID orgId = extractOrgId(auth);
        var pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<TransactionResponse> result = transactionService.findByOrganization(orgId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable UUID id,
            Authentication auth) {
        UUID orgId = extractOrgId(auth);
        TransactionResponse response = transactionService.findById(id, orgId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID extractOrgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
