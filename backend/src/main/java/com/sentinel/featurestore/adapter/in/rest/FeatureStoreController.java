package com.sentinel.featurestore.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.featurestore.application.service.FeatureComputationService;
import com.sentinel.featurestore.domain.model.FeatureDefinition;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
public class FeatureStoreController {

    private final FeatureComputationService featureComputationService;
    private final TransactionRepository transactionRepository;

    /** The catalogue, including each feature's window — what a data scientist reads first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> catalogue() {
        List<Map<String, String>> catalogue = new ArrayList<>();
        for (FeatureDefinition feature : FeatureDefinition.values()) {
            catalogue.add(Map.of(
                    "key", feature.getKey(),
                    "label", feature.getLabel(),
                    "type", feature.getType(),
                    "window", feature.getWindow(),
                    "description", feature.getDescription()));
        }
        return ResponseEntity.ok(ApiResponse.success(catalogue));
    }

    /**
     * The feature vector as it stood at the transaction's own timestamp, not
     * as it stands now.
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forTransaction(
            @PathVariable UUID transactionId, Authentication auth) {
        UUID orgId = orgId(auth);
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId.toString()));
        return ResponseEntity.ok(ApiResponse.success(featureComputationService.compute(orgId, transaction)));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
