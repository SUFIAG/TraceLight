package com.sentinel.fraud.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.fraud.application.service.FraudAnalysisService;
import com.sentinel.fraud.risk.adapter.out.persistence.RiskScoreRepository;
import com.sentinel.fraud.risk.application.dto.RiskScoreResponse;
import com.sentinel.transaction.application.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudAnalysisService fraudAnalysisService;
    private final TransactionService transactionService;
    private final RiskScoreRepository riskScoreRepository;

    @PostMapping("/analyze/{transactionId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> analyzeTransaction(
            @PathVariable UUID transactionId,
            Authentication auth) {
        // Resolving through the service first proves the transaction belongs to the
        // caller's organization before any analysis runs.
        transactionService.findById(transactionId, organizationId(auth));
        fraudAnalysisService.analyzeTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("status", "ANALYZED", "transactionId", transactionId.toString())));
    }

    @GetMapping("/risk-score/{transactionId}")
    public ResponseEntity<ApiResponse<RiskScoreResponse>> getRiskScore(
            @PathVariable UUID transactionId,
            Authentication auth) {
        transactionService.findById(transactionId, organizationId(auth));
        RiskScoreResponse response = riskScoreRepository.findByTransactionId(transactionId)
                .map(RiskScoreResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("RiskScore", transactionId.toString()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID organizationId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
