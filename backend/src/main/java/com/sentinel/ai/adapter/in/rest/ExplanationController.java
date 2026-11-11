package com.sentinel.ai.adapter.in.rest;

import com.sentinel.ai.application.dto.ExplanationResponse;
import com.sentinel.ai.application.service.ExplanationService;
import com.sentinel.alert.application.service.AlertService;
import com.sentinel.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/explanations")
@RequiredArgsConstructor
public class ExplanationController {

    private final ExplanationService explanationService;
    private final AlertService alertService;

    @GetMapping("/alert/{alertId}")
    public ResponseEntity<ApiResponse<ExplanationResponse>> getByAlert(
            @PathVariable UUID alertId,
            Authentication auth) {
        UUID organizationId = UUID.fromString((String) auth.getCredentials());
        // Resolving the alert first proves it belongs to the caller's organization.
        // Without this, any authenticated user could read another tenant's explanation
        // by guessing an alert id.
        alertService.findById(alertId, organizationId);
        return ResponseEntity.ok(ApiResponse.success(explanationService.findByAlertId(alertId)));
    }
}
