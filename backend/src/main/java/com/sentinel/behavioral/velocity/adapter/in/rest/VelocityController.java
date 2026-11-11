package com.sentinel.behavioral.velocity.adapter.in.rest;

import com.sentinel.behavioral.velocity.application.dto.VelocityResponse;
import com.sentinel.behavioral.velocity.application.service.VelocityAnalyzer;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/velocity")
@RequiredArgsConstructor
public class VelocityController {

    private final VelocityAnalyzer velocityAnalyzer;
    private final TransactionRepository transactionRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<VelocityResponse>> getVelocity(
            @PathVariable String userId,
            Authentication auth) {
        UUID organizationId = UUID.fromString((String) auth.getCredentials());
        // userId is a customer identifier supplied by the caller, so it must be
        // proven to belong to this organization before any behaviour is returned.
        if (transactionRepository.findByOrganizationIdAndUserId(organizationId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Customer", userId);
        }
        return ResponseEntity.ok(ApiResponse.success(velocityAnalyzer.analyze(organizationId, userId)));
    }
}
